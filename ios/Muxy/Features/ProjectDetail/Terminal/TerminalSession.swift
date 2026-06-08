import Foundation
import Observation
import OSLog
import SwiftTerm
import UIKit

@MainActor
@Observable
final class TerminalSession {
    enum Ownership: Equatable {
        case idle
        case takingOver
        case owned
        case controlledElsewhere(deviceName: String)
        case disconnected
    }

    let paneID: UUID

    private(set) var ownership: Ownership = .idle
    private(set) var isFollowingBottom = true
    private(set) var title = ""
    private(set) var theme = TerminalTheme(clientTheme: .dark)
    private(set) var activeModifier: TerminalModifier = .ctrl
    private(set) var modifierArmed = false

    @ObservationIgnored var onModifierStateChange: ((TerminalModifier, Bool) -> Void)?
    @ObservationIgnored private weak var view: TerminalView?
    @ObservationIgnored private let channel: TerminalChannel
    @ObservationIgnored private var clientID: UUID?
    @ObservationIgnored private var eventsTask: Task<Void, Never>?
    @ObservationIgnored private var resizeTask: Task<Void, Never>?
    @ObservationIgnored private var scrollRestoreTask: Task<Void, Never>?
    @ObservationIgnored private var hasTakenOver = false
    @ObservationIgnored private var isActive = false
    @ObservationIgnored private var lastTakeOverAt: Date?
    @ObservationIgnored private var lastReportedSize: (cols: Int, rows: Int)?
    @ObservationIgnored private var clientTheme: ClientTerminalTheme = .dark
    @ObservationIgnored private var lastSentClientTheme: ClientTerminalTheme?

    private static let followEpsilon = 0.001
    private static let resizeDebounce = Duration.milliseconds(120)
    private static let takeOverGraceSeconds: TimeInterval = 2

    init(paneID: UUID, channel: TerminalChannel) {
        self.paneID = paneID
        self.channel = channel
    }

    func attach(_ view: TerminalView) {
        self.view = view
    }

    @discardableResult
    func dismissKeyboard() -> Bool {
        view?.resignFirstResponder() ?? false
    }

    var canCopySelection: Bool {
        guard let selection = view?.getSelection() else { return false }
        return !selection.isEmpty
    }

    func activate() {
        isActive = true
        ownership = .takingOver
        hasTakenOver = false
        if eventsTask == nil {
            startConsumingEvents()
        }
        Task { await loadConnectionContext() }
        sendClientThemeIfNeeded(force: true)
        takeOverIfReady()
    }

    func deactivate() {
        isActive = false
        eventsTask?.cancel()
        eventsTask = nil
        resizeTask?.cancel()
        resizeTask = nil
        scrollRestoreTask?.cancel()
        scrollRestoreTask = nil
        hasTakenOver = false
        lastReportedSize = nil
        ownership = .idle
        Task { [channel, paneID] in
            try? await channel.notify(.releasePane, params: ReleasePaneParams(paneID: paneID.uuidString))
        }
    }

    func takeOverIfReady() {
        guard isActive, !hasTakenOver, let view, view.bounds.width > 0, view.bounds.height > 0 else { return }
        let terminal = view.getTerminal()
        bootstrapTakeover(cols: terminal.cols, rows: terminal.rows)
    }

    func handleConnectionState(_ state: ConnectionState) {
        switch state {
        case .connected:
            guard ownership == .disconnected else { return }
            sendClientThemeIfNeeded(force: true)
            rearmTakeover()
        case .connecting, .authenticating, .idle:
            break
        case .disconnected, .failed:
            ownership = .disconnected
            hasTakenOver = false
            lastReportedSize = nil
        }
    }

    func bootstrapTakeover(cols: Int, rows: Int) {
        guard !hasTakenOver else {
            reportResize(cols: cols, rows: rows)
            return
        }
        hasTakenOver = true
        lastReportedSize = (cols, rows)
        markTakeOver()
        ownership = .takingOver
        Log.terminal.debug("bootstrapTakeover \(cols, privacy: .public)x\(rows, privacy: .public) pane \(self.paneID.uuidString, privacy: .public)")
        Task { [weak self, channel, paneID] in
            let params = TakeOverPaneParams(paneID: paneID.uuidString, cols: cols, rows: rows)
            do {
                _ = try await channel.request(.takeOverPane, params: params)
                Log.terminal.debug("takeOverPane ok pane \(paneID.uuidString, privacy: .public)")
                await self?.confirmTakeOver(for: paneID)
            } catch {
                Log.terminal.error("takeOverPane failed: \(String(describing: error), privacy: .public)")
                await self?.failTakeOver(for: paneID)
            }
        }
    }

    private func markTakeOver() {
        lastTakeOverAt = Date()
    }

    private func withinTakeOverGrace() -> Bool {
        guard let lastTakeOverAt else { return false }
        return Date().timeIntervalSince(lastTakeOverAt) < TerminalSession.takeOverGraceSeconds
    }

    private func confirmTakeOver(for paneID: UUID) {
        guard isActive, self.paneID == paneID, hasTakenOver else { return }
        markTakeOver()
        ownership = .owned
    }

    private func failTakeOver(for paneID: UUID) {
        guard isActive, self.paneID == paneID else { return }
        hasTakenOver = false
    }

    func reportResize(cols: Int, rows: Int) {
        guard hasTakenOver else { return }
        guard lastReportedSize?.cols != cols || lastReportedSize?.rows != rows else { return }
        resizeTask?.cancel()
        resizeTask = Task { [weak self, channel, paneID] in
            try? await Task.sleep(for: TerminalSession.resizeDebounce)
            guard !Task.isCancelled else { return }
            let params = TerminalResizeParams(paneID: paneID.uuidString, cols: cols, rows: rows)
            do {
                _ = try await channel.request(.terminalResize, params: params)
                await self?.markResized(cols: cols, rows: rows)
            } catch {
                Log.terminal.error("terminalResize failed: \(String(describing: error), privacy: .public)")
            }
        }
    }

    func sendBytes(_ slice: ArraySlice<UInt8>) {
        guard modifierArmed,
              let text = String(bytes: slice, encoding: .utf8),
              let transformed = TerminalInputEncoding.apply(activeModifier, to: text)
        else {
            transmit(Array(slice))
            return
        }
        setModifierState(activeModifier, armed: false)
        transmit(Array(transformed.utf8))
    }

    func sendText(_ text: String) {
        transmit(Array(text.utf8))
    }

    func setModifierArmed(_ armed: Bool) {
        setModifierState(activeModifier, armed: armed)
    }

    func selectModifier(_ modifier: TerminalModifier) {
        setModifierState(modifier, armed: false)
    }

    private func setModifierState(_ modifier: TerminalModifier, armed: Bool) {
        activeModifier = modifier
        modifierArmed = armed
        onModifierStateChange?(modifier, armed)
    }

    func useClientTheme(_ clientTheme: ClientTerminalTheme) {
        guard self.clientTheme != clientTheme else { return }
        self.clientTheme = clientTheme
        theme = TerminalTheme(clientTheme: clientTheme)
        sendClientThemeIfNeeded(force: false)
    }

    func copySelection() {
        guard let selection = view?.getSelection(), !selection.isEmpty else { return }
        UIPasteboard.general.string = selection
    }

    func paste() {
        guard let text = UIPasteboard.general.string, !text.isEmpty else { return }
        transmit(Array(text.utf8))
    }

    func setTitle(_ title: String) {
        self.title = title
    }

    func userScrolled(toPosition position: Double) {
        isFollowingBottom = position >= 1 - TerminalSession.followEpsilon
    }

    func requestJumpToBottom() {
        scrollRestoreTask?.cancel()
        scrollRestoreTask = nil
        if let view {
            TerminalScrollOffset.scrollToBottom(view)
        }
        isFollowingBottom = true
    }

    func takeControl() {
        rearmTakeover()
    }

    private func transmit(_ bytes: [UInt8]) {
        guard !bytes.isEmpty else { return }
        guard ownership == .owned || ownership == .takingOver else { return }
        Log.terminal.debug("transmit \(bytes.count, privacy: .public) bytes for pane \(self.paneID.uuidString, privacy: .public) ownership=\(String(describing: self.ownership), privacy: .public)")
        Task { [channel, paneID] in
            let params = TerminalInputParams(paneID: paneID.uuidString, bytes: Data(bytes))
            do {
                try await channel.notify(.terminalInput, params: params)
            } catch {
                Log.terminal.error("terminalInput failed: \(String(describing: error), privacy: .public)")
            }
        }
    }

    private func rearmTakeover() {
        ownership = .takingOver
        hasTakenOver = false
        lastReportedSize = nil
        markTakeOver()
        takeOverIfReady()
    }

    private func markResized(cols: Int, rows: Int) {
        lastReportedSize = (cols, rows)
    }

    private func startConsumingEvents() {
        eventsTask = Task { [weak self, channel] in
            let stream = await channel.events()
            for await event in stream {
                guard let self else { return }
                self.handle(event)
            }
        }
    }

    private func loadConnectionContext() async {
        clientID = await channel.currentClientID
    }

    private func handle(_ event: EventEnvelope) {
        switch event.event {
        case EventName.terminalOutput:
            handleBytes(event, expectedType: EventType.terminalOutput)
        case EventName.terminalSnapshot:
            handleBytes(event, expectedType: EventType.terminalSnapshot)
        case EventName.paneOwnershipChanged:
            handleOwnership(event)
        case EventName.themeChanged:
            handleTheme(event)
        default:
            break
        }
    }

    private func handleBytes(_ event: EventEnvelope, expectedType: String) {
        guard let data = event.data, data.type == expectedType else { return }
        guard let payload = try? data.decode(TerminalBytesEvent.self) else { return }
        guard payload.paneID == paneID else { return }
        guard let view else { return }

        let shouldFollowBottom = isFollowingBottom && TerminalScrollOffset.isAtBottom(view)
        if shouldFollowBottom {
            feed(payload.bytes, into: view)
            view.scroll(toPosition: 1)
            isFollowingBottom = true
            return
        }

        let scrollOffset = TerminalScrollOffset.capture(from: view)
        feed(payload.bytes, into: view)
        if !TerminalScrollOffset.isInteracting(with: view) {
            scrollOffset.restore(on: view)
        }
        scrollRestoreTask?.cancel()
        scrollRestoreTask = Task { @MainActor [weak self, weak view] in
            try? await Task.sleep(for: .milliseconds(20))
            guard !Task.isCancelled else { return }
            guard let view else { return }
            if TerminalScrollOffset.isAtBottom(view) {
                scrollOffset.restore(on: view)
            }
            self?.isFollowingBottom = false
        }
        isFollowingBottom = false
    }

    private func feed(_ bytes: Data, into view: TerminalView) {
        if let view = view as? FollowAwareTerminalView {
            view.preserveInteractiveOffsetDuringTerminalUpdate {
                view.feed(byteArray: ArraySlice(bytes))
            }
            return
        }
        view.feed(byteArray: ArraySlice(bytes))
    }

    private func handleOwnership(_ event: EventEnvelope) {
        guard let data = event.data, data.type == EventType.paneOwnership else { return }
        guard let payload = try? data.decode(PaneOwnershipEvent.self) else { return }
        guard payload.paneID == paneID else { return }

        if ownedByUs(payload.owner) {
            if ownership != .owned {
                ownership = .owned
            }
            return
        }

        guard !withinTakeOverGrace() else { return }

        switch ownership {
        case .owned, .takingOver:
            ownership = .controlledElsewhere(deviceName: ownerName(payload.owner))
        case .idle, .controlledElsewhere, .disconnected:
            break
        }
    }

    private func ownedByUs(_ owner: PaneOwner) -> Bool {
        guard case let .remote(ownerID, _) = owner, let clientID else { return false }
        return ownerID == clientID
    }

    private func ownerName(_ owner: PaneOwner) -> String {
        switch owner {
        case let .mac(deviceName):
            return deviceName
        case let .remote(_, deviceName):
            return deviceName
        }
    }

    private func handleTheme(_ event: EventEnvelope) {
        guard let data = event.data, data.type == EventType.deviceTheme else { return }
        guard let payload = try? data.decode(DeviceThemeEvent.self) else { return }
        applyTheme(payload)
    }

    private func applyTheme(_ event: DeviceThemeEvent) {
        guard lastSentClientTheme == nil else { return }
        theme = TerminalTheme(event: event)
    }

    private func sendClientThemeIfNeeded(force: Bool) {
        guard force || lastSentClientTheme != clientTheme else { return }
        lastSentClientTheme = clientTheme
        let params = SetClientThemeParams(theme: clientTheme)
        Task { [channel] in
            do {
                _ = try await channel.request(.setClientTheme, params: params)
            } catch let error as ProtocolError where error.code == .notFound {
                Log.terminal.debug("setClientTheme unsupported")
            } catch {
                Log.terminal.error("setClientTheme failed: \(String(describing: error), privacy: .public)")
            }
        }
    }
}

private struct TerminalScrollOffset {
    private static let bottomTolerance: CGFloat = 2
    private static let minimumDetachedDistance: CGFloat = 0

    let y: CGFloat

    static func capture(from view: TerminalView) -> TerminalScrollOffset {
        let maxOffset = max(0, view.contentSize.height - view.bounds.height)
        let detachedY = min(view.contentOffset.y, max(0, maxOffset - minimumDetachedDistance))
        return TerminalScrollOffset(y: max(0, detachedY))
    }

    static func isAtBottom(_ view: TerminalView) -> Bool {
        let maxOffset = max(0, view.contentSize.height - view.bounds.height)
        return view.contentOffset.y >= maxOffset - bottomTolerance
    }

    static func scrollToBottom(_ view: TerminalView) {
        view.scroll(toPosition: 1)
        let maxOffset = max(0, view.contentSize.height - view.bounds.height)
        view.contentOffset = CGPoint(x: view.contentOffset.x, y: maxOffset)
    }

    static func isInteracting(with view: TerminalView) -> Bool {
        view.isTracking || view.isDragging || view.isDecelerating
    }

    func restore(on view: TerminalView) {
        let maxOffset = max(0, view.contentSize.height - view.bounds.height)
        guard maxOffset > 0 else { return }
        let y = min(y, maxOffset)
        let position = Double(y / maxOffset)
        view.scroll(toPosition: position)
        view.contentOffset = CGPoint(x: view.contentOffset.x, y: y)
    }
}
