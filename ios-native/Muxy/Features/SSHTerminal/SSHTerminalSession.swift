import Foundation
import Observation
import OSLog
import SwiftTerm
import UIKit

@MainActor
@Observable
final class SSHTerminalSession: TerminalIO {
    let tabID: UUID

    private(set) var isFollowingBottom = true
    private(set) var title = ""
    private(set) var theme = TerminalTheme(clientTheme: .dark)
    private(set) var activeModifier: TerminalModifier = .ctrl
    private(set) var modifierArmed = false
    private(set) var state: SSHConnectionState = .idle

    @ObservationIgnored var onModifierStateChange: ((TerminalModifier, Bool) -> Void)?
    @ObservationIgnored private weak var view: TerminalView?
    @ObservationIgnored private let session: SSHSession
    @ObservationIgnored private var outputTask: Task<Void, Never>?
    @ObservationIgnored private var stateTask: Task<Void, Never>?
    @ObservationIgnored private var resizeTask: Task<Void, Never>?
    @ObservationIgnored private var scrollRestoreTask: Task<Void, Never>?
    @ObservationIgnored private var isActive = false
    @ObservationIgnored private var hasStarted = false
    @ObservationIgnored private var lastReportedSize: (cols: Int, rows: Int)?
    @ObservationIgnored private var clientTheme: ClientTerminalTheme = .dark

    private static let resizeDebounce = Duration.milliseconds(120)
    private static let minimumUsableCols = 20
    private static let minimumUsableRows = 4

    init(tabID: UUID, session: SSHSession) {
        self.tabID = tabID
        self.session = session
    }

    func attach(_ view: TerminalView) {
        self.view = view
    }

    func activate() {
        guard !isActive else { return }
        isActive = true
        consumeOutput()
        observeState()
        startIfReady()
    }

    func deactivate() {
        view?.resignFirstResponder()
    }

    func teardown() {
        isActive = false
        outputTask?.cancel()
        outputTask = nil
        stateTask?.cancel()
        stateTask = nil
        resizeTask?.cancel()
        resizeTask = nil
        scrollRestoreTask?.cancel()
        scrollRestoreTask = nil
        Task { [session] in await session.close() }
    }

    func terminalDidLayout() {
        startIfReady()
    }

    func terminalDidResize(cols: Int, rows: Int) {
        startIfReady()
        reportResize(cols: cols, rows: rows)
    }

    var canCopySelection: Bool {
        guard let selection = view?.getSelection() else { return false }
        return !selection.isEmpty
    }

    @discardableResult
    func dismissKeyboard() -> Bool {
        view?.resignFirstResponder() ?? false
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

    func paste() {
        guard let text = UIPasteboard.general.string, !text.isEmpty else { return }
        transmit(Array(text.utf8))
    }

    func copySelection() {
        guard let selection = view?.getSelection(), !selection.isEmpty else { return }
        UIPasteboard.general.string = selection
    }

    func setModifierArmed(_ armed: Bool) {
        setModifierState(activeModifier, armed: armed)
    }

    func selectModifier(_ modifier: TerminalModifier) {
        setModifierState(modifier, armed: false)
    }

    func setTitle(_ title: String) {
        self.title = title
    }

    func userScrolled(toPosition position: Double) {
        isFollowingBottom = TerminalOutputFeeder.isFollowingBottom(position)
    }

    func requestJumpToBottom() {
        scrollRestoreTask?.cancel()
        scrollRestoreTask = nil
        if let view {
            TerminalOutputFeeder.scrollToBottom(view)
        }
        isFollowingBottom = true
    }

    func useClientTheme(_ clientTheme: ClientTerminalTheme) {
        guard self.clientTheme != clientTheme else { return }
        self.clientTheme = clientTheme
        theme = TerminalTheme(clientTheme: clientTheme)
    }

    func retry() {
        hasStarted = false
        lastReportedSize = nil
        startIfReady()
    }

    private func startIfReady() {
        guard isActive, !hasStarted, let size = laidOutTerminalSize() else { return }
        hasStarted = true
        lastReportedSize = size
        Task { [session] in await session.connect(cols: size.cols, rows: size.rows) }
    }

    private func reportResize(cols: Int, rows: Int) {
        guard hasStarted else { return }
        guard cols >= SSHTerminalSession.minimumUsableCols, rows >= SSHTerminalSession.minimumUsableRows else { return }
        guard lastReportedSize?.cols != cols || lastReportedSize?.rows != rows else { return }
        resizeTask?.cancel()
        resizeTask = Task { [weak self, session] in
            try? await Task.sleep(for: SSHTerminalSession.resizeDebounce)
            guard !Task.isCancelled else { return }
            await session.resize(cols: cols, rows: rows)
            await self?.markResized(cols: cols, rows: rows)
        }
    }

    private func markResized(cols: Int, rows: Int) {
        lastReportedSize = (cols, rows)
    }

    private func laidOutTerminalSize() -> (cols: Int, rows: Int)? {
        guard let view, view.bounds.width > 0, view.bounds.height > 0 else { return nil }
        let terminal = view.getTerminal()
        let cols = terminal.cols
        let rows = terminal.rows
        guard cols >= SSHTerminalSession.minimumUsableCols, rows >= SSHTerminalSession.minimumUsableRows else { return nil }
        return (cols, rows)
    }

    private func transmit(_ bytes: [UInt8]) {
        guard !bytes.isEmpty, state == .connected else { return }
        Task { [session] in await session.send(Data(bytes)) }
    }

    private func setModifierState(_ modifier: TerminalModifier, armed: Bool) {
        activeModifier = modifier
        modifierArmed = armed
        onModifierStateChange?(modifier, armed)
    }

    private func consumeOutput() {
        guard outputTask == nil else { return }
        outputTask = Task { [weak self, session] in
            let stream = await session.output()
            for await data in stream {
                guard let self else { return }
                self.feed(data)
            }
        }
    }

    private func observeState() {
        guard stateTask == nil else { return }
        stateTask = Task { [weak self, session] in
            let stream = await session.stateUpdates()
            for await state in stream {
                guard let self else { return }
                self.state = state
            }
        }
    }

    private func feed(_ data: Data) {
        guard let view else { return }
        let scrollOffset = TerminalScrollOffset.capture(from: view)
        let followedBottom = TerminalOutputFeeder.feedFollowingBottom(data, into: view, isFollowingBottom: isFollowingBottom)
        if followedBottom {
            isFollowingBottom = true
            return
        }
        scrollRestoreTask?.cancel()
        scrollRestoreTask = Task { @MainActor [weak self, weak view] in
            try? await Task.sleep(for: .milliseconds(20))
            guard !Task.isCancelled, let view else { return }
            if TerminalScrollOffset.isAtBottom(view) {
                scrollOffset.restore(on: view)
            }
            self?.isFollowingBottom = false
        }
        isFollowingBottom = false
    }
}
