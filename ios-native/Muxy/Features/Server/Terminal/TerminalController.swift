import Foundation
import MuxyMobile
import Observation
import OSLog

@MainActor
protocol TerminalDisplay: AnyObject {
    func screenNeedsRefresh()
    func prepareForLiveOutput()
}

@MainActor
@Observable
final class TerminalController: Identifiable, TabStripItem {
    enum Phase: Equatable {
        case waiting
        case creating
        case attaching
        case live
        case failed(String)
        case ended
    }

    enum Mode: Equatable {
        case live
        case history
    }

    static let maximumInputBytes = 1_048_576
    private static let bracketedPasteWrapperBytes = 12
    private static let historyPageRows: UInt16 = 200
    private static let olderPageRows: UInt16 = 500
    private static let minimumColumns: UInt16 = 20
    private static let minimumRows: UInt16 = 4
    private static let resizeDebounce = Duration.milliseconds(120)

    nonisolated let id = UUID()
    let projectID: String
    private(set) var sessionID: UInt64?
    private(set) var title: String
    private(set) var phase: Phase = .waiting
    private(set) var mode: Mode = .live
    private(set) var isFollowing = true
    private(set) var notice: String?
    private(set) var historyReachedStart = false
    private(set) var isAtHistoryTop = false

    @ObservationIgnored weak var server: ServerController?
    @ObservationIgnored weak var display: (any TerminalDisplay)?
    @ObservationIgnored var onModifierStateChange: ((TerminalModifier, Bool) -> Void)?
    @ObservationIgnored private(set) var channel: (any ServerTerminalChannel)?
    @ObservationIgnored private(set) var cachedScreen: Screen?
    @ObservationIgnored private(set) var viewportSize: TerminalGridSize?
    @ObservationIgnored private var requestedSize: TerminalGridSize?
    @ObservationIgnored private var resizeTask: Task<Void, Never>?
    @ObservationIgnored private(set) var history: HistoryDocument?
    @ObservationIgnored private(set) var activeModifier: TerminalModifier = .ctrl
    @ObservationIgnored private(set) var modifierArmed = false
    @ObservationIgnored private(set) var isRetired = false
    @ObservationIgnored private var noticeTask: Task<Void, Never>?

    init(projectID: String, sessionID: UInt64?, server: ServerController) {
        self.projectID = projectID
        self.sessionID = sessionID
        self.server = server
        title = Self.defaultTitle(for: sessionID)
    }

    var systemImage: String {
        "terminal"
    }

    var isLive: Bool {
        phase == .live
    }

    var canAttach: Bool {
        phase == .waiting
    }

    var showsHistoryStart: Bool {
        mode == .history && historyReachedStart && isAtHistoryTop
    }

    func screenDidChange() {
        display?.screenNeedsRefresh()
    }

    func metadataDidChange() {
        guard let channel else { return }
        updateTitle(from: channel.screen())
        display?.screenNeedsRefresh()
    }

    func currentScreen() -> Screen? {
        guard let channel else { return cachedScreen }
        let screen = channel.screen()
        cachedScreen = screen
        return screen
    }

    func viewportDidChange(_ size: TerminalGridSize) {
        guard size.columns >= Self.minimumColumns, size.rows >= Self.minimumRows else { return }
        guard viewportSize != size else { return }
        viewportSize = size
        server?.viewportDidChange(of: self)
        scheduleFit()
    }

    func assign(sessionID: UInt64) {
        self.sessionID = sessionID
        title = Self.defaultTitle(for: sessionID)
    }

    func markCreating() {
        phase = .creating
    }

    func markAttaching() {
        phase = .attaching
    }

    func didAttach(_ channel: any ServerTerminalChannel) {
        self.channel = channel
        phase = .live
        let screen = channel.screen()
        cachedScreen = screen
        updateTitle(from: screen)
        display?.screenNeedsRefresh()
        fitToViewport()
    }

    func releaseChannel() -> (any ServerTerminalChannel)? {
        let released = channel
        channel = nil
        cancelFit()
        leaveHistory()
        if phase == .live || phase == .attaching {
            phase = .waiting
        }
        return released
    }

    func connectionDidClose() {
        channel = nil
        cancelFit()
        leaveHistory()
        guard phase != .ended else { return }
        if case .failed = phase { return }
        phase = .waiting
    }

    func attachWasInterrupted() {
        guard phase == .creating || phase == .attaching else { return }
        phase = .waiting
    }

    func markRetired() {
        isRetired = true
    }

    func attachDidFail(_ failure: ServerFailure) {
        channel = nil
        cancelFit()
        phase = .failed(failure.message(context: .request, serverName: server?.serverName ?? "the computer"))
    }

    func markEnded() {
        channel = nil
        cancelFit()
        leaveHistory()
        phase = .ended
    }

    func retry() {
        guard case .failed = phase else { return }
        phase = .waiting
        server?.updateVisibleTerminal()
    }

    func setFollowing(_ following: Bool) {
        guard isFollowing != following else { return }
        isFollowing = following
    }

    func sendText(_ text: String) {
        guard let channel, !text.isEmpty else { return }
        returnToLive()
        if modifierArmed, let stroke = stickyStroke(for: text) {
            setModifierState(activeModifier, armed: false)
            channel.send(key: stroke.sdkKey, modifiers: stroke.sdkModifiers)
            return
        }
        switch text {
        case "\n", "\r":
            channel.send(key: .enter, modifiers: Self.noModifiers)
        case "\t":
            channel.send(key: .tab, modifiers: Self.noModifiers)
        default:
            channel.send(text: Self.normalizedLineBreaks(text))
        }
    }

    func send(_ stroke: TerminalKeyStroke) {
        guard let channel else { return }
        returnToLive()
        guard modifierArmed else {
            channel.send(key: stroke.sdkKey, modifiers: stroke.sdkModifiers)
            return
        }
        let armed = TerminalKeyStroke(stroke.key, modifiers: stroke.modifiers.union(activeModifier.keyModifiers))
        setModifierState(activeModifier, armed: false)
        channel.send(key: armed.sdkKey, modifiers: armed.sdkModifiers)
    }

    func paste(_ text: String) {
        guard let channel, !text.isEmpty else { return }
        guard text.utf8.count <= Self.maximumInputBytes - Self.bracketedPasteWrapperBytes else {
            show(notice: "That text is too large to paste. The limit is 1 MB.")
            return
        }
        returnToLive()
        channel.paste(text)
    }

    func setModifierArmed(_ armed: Bool) {
        setModifierState(activeModifier, armed: armed)
    }

    func selectModifier(_ modifier: TerminalModifier) {
        setModifierState(modifier, armed: false)
    }

    func enterHistory() async -> HistoryDocument? {
        guard mode == .live, history == nil, let channel else { return nil }
        let screenRows = Int(cachedScreen?.rows ?? 0)
        do {
            let snapshot = try await channel.scrollback(maxRows: Self.historyPageRows)
            guard self.channel === channel else { return nil }
            let document = HistoryDocument(snapshot: snapshot, screenRows: screenRows)
            history = document
            historyReachedStart = document.reachedStart
            isAtHistoryTop = false
            mode = .history
            setFollowing(false)
            return document
        } catch {
            Log.terminal.error("Scrollback failed: \(String(describing: ServerFailure(error)), privacy: .public)")
            return nil
        }
    }

    func loadOlderHistory() async -> Int {
        guard let history else { return 0 }
        let added = await history.loadOlder(maxRows: Self.olderPageRows)
        guard self.history === history else { return added }
        historyReachedStart = history.reachedStart
        return added
    }

    func setAtHistoryTop(_ atTop: Bool) {
        guard isAtHistoryTop != atTop else { return }
        isAtHistoryTop = atTop
    }

    func returnToLive() {
        leaveHistory()
        setFollowing(true)
        display?.prepareForLiveOutput()
        display?.screenNeedsRefresh()
    }

    private func scheduleFit() {
        guard channel != nil else { return }
        resizeTask?.cancel()
        resizeTask = Task { [weak self] in
            try? await Task.sleep(for: Self.resizeDebounce)
            guard !Task.isCancelled else { return }
            self?.fitToViewport()
        }
    }

    private func fitToViewport() {
        guard let channel, let size = viewportSize, size != requestedSize else { return }
        requestedSize = size
        if let screen = cachedScreen, screen.columns == size.columns, screen.rows == size.rows { return }
        Task { [weak self] in
            do {
                try await channel.resize(to: size)
            } catch {
                Log.terminal.error("Resize failed: \(String(describing: ServerFailure(error)), privacy: .public)")
                self?.resizeDidFail(on: channel)
            }
        }
    }

    private func resizeDidFail(on failedChannel: any ServerTerminalChannel) {
        guard channel === failedChannel else { return }
        requestedSize = nil
    }

    private func cancelFit() {
        resizeTask?.cancel()
        resizeTask = nil
        requestedSize = nil
    }

    private func leaveHistory() {
        history = nil
        mode = .live
        historyReachedStart = false
        isAtHistoryTop = false
    }

    private func stickyStroke(for text: String) -> TerminalKeyStroke? {
        guard text.count == 1 else { return nil }
        switch activeModifier {
        case .ctrl:
            return TerminalKeyStroke(.character(text), modifiers: .control)
        case .alt:
            return TerminalKeyStroke(.character(text), modifiers: .alt)
        case .shift:
            return TerminalKeyStroke(.character(text.uppercased()))
        }
    }

    private func setModifierState(_ modifier: TerminalModifier, armed: Bool) {
        activeModifier = modifier
        modifierArmed = armed
        onModifierStateChange?(modifier, armed)
    }

    private func updateTitle(from screen: Screen) {
        let programTitle = screen.title.trimmingCharacters(in: .whitespacesAndNewlines)
        if !programTitle.isEmpty {
            title = programTitle
            return
        }
        let folder = (screen.directory as NSString).lastPathComponent
        title = folder.isEmpty ? Self.defaultTitle(for: sessionID) : folder
    }

    private func show(notice message: String) {
        notice = message
        noticeTask?.cancel()
        noticeTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(4))
            guard !Task.isCancelled else { return }
            self?.notice = nil
        }
    }

    private static let noModifiers = Modifiers(shift: false, alt: false, control: false)

    private static func defaultTitle(for sessionID: UInt64?) -> String {
        sessionID.map { "Terminal \($0)" } ?? "New Terminal"
    }

    private static func normalizedLineBreaks(_ text: String) -> String {
        text.replacingOccurrences(of: "\r\n", with: "\r").replacingOccurrences(of: "\n", with: "\r")
    }
}

extension TerminalModifier {
    var keyModifiers: TerminalKeyModifiers {
        switch self {
        case .ctrl:
            return .control
        case .alt:
            return .alt
        case .shift:
            return .shift
        }
    }
}
