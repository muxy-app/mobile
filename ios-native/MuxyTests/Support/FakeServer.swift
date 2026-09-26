import Foundation
import MuxyMobile
import Synchronization
@testable import Muxy

extension Fixtures {
    static func screen(title: String = "", columns: UInt16 = 80, rows: UInt16 = 24) -> Screen {
        Screen(
            columns: columns,
            rows: rows,
            lines: [],
            cursor: Cursor(row: 0, column: 0, visible: true, shape: .block),
            title: title,
            directory: "/Users/demo/muxy",
            historyRows: 0,
            applicationCursorKeys: false,
            bracketedPaste: false,
            mouseTracking: false,
            alternateScroll: false
        )
    }

    static func session(_ id: UInt64, project: String = "muxy", status: SessionStatus = .live) -> Session {
        Session(id: id, projectId: project, directory: "/Users/demo/muxy", status: status, owner: .desktop, attached: false)
    }
}

final class FakeTerminalChannel: ServerTerminalChannel {
    let sessionId: UInt64

    private let state: Mutex<State>

    struct State {
        var screen: Screen
        var sent: [String] = []
        var pastes: [String] = []
        var resizes: [TerminalGridSize] = []
        var detachCount = 0
    }

    init(sessionId: UInt64, screen: Screen) {
        self.sessionId = sessionId
        state = Mutex(State(screen: screen))
    }

    var sent: [String] {
        state.withLock { $0.sent }
    }

    var pastes: [String] {
        state.withLock { $0.pastes }
    }

    var resizes: [TerminalGridSize] {
        state.withLock { $0.resizes }
    }

    var detachCount: Int {
        state.withLock { $0.detachCount }
    }

    func screen() -> Screen {
        state.withLock { $0.screen }
    }

    func send(text: String) {
        state.withLock { $0.sent.append("text:\(text)") }
    }

    func send(key: Key, modifiers: Modifiers) {
        let control = modifiers.control ? "ctrl+" : ""
        state.withLock { $0.sent.append("key:\(control)\(key)") }
    }

    func paste(_ text: String) {
        state.withLock { $0.pastes.append(text) }
    }

    func resize(to size: TerminalGridSize) async throws {
        state.withLock { $0.resizes.append(size) }
    }

    func scrollback(maxRows: UInt16) async throws -> any ScrollbackSnapshot {
        StubScrollbackSnapshot(lines: [], historyRows: 0, olderPages: [])
    }

    func detach() async throws {
        state.withLock { $0.detachCount += 1 }
    }
}

final class FakeServerConnection: ServerConnection {
    private let state: Mutex<State>

    struct State {
        var projects: [ServerProject]
        var sessions: [Session]
        var attachFailures: [UInt64: MobileError] = [:]
        var channels: [UInt64: FakeTerminalChannel] = [:]
        var attached: [UInt64] = []
        var created: [TerminalGridSize] = []
        var ended: [UInt64] = []
        var nextSessionID: UInt64 = 900
        var isDisconnected = false
    }

    init(
        projects: [ServerProject] = [
            Fixtures.project(id: "home", name: "Home", isHome: true),
            Fixtures.project(id: "muxy", name: "muxy"),
        ],
        sessions: [Session] = [],
        attachFailures: [UInt64: MobileError] = [:]
    ) {
        state = Mutex(State(projects: projects, sessions: sessions, attachFailures: attachFailures))
    }

    var attached: [UInt64] {
        state.withLock { $0.attached }
    }

    var created: [TerminalGridSize] {
        state.withLock { $0.created }
    }

    var ended: [UInt64] {
        state.withLock { $0.ended }
    }

    var isDisconnected: Bool {
        state.withLock { $0.isDisconnected }
    }

    func channel(for sessionId: UInt64) -> FakeTerminalChannel? {
        state.withLock { $0.channels[sessionId] }
    }

    var serverVersion: String {
        "test"
    }

    func projects() async throws -> [ServerProject] {
        try checkOpen()
        return state.withLock { $0.projects }
    }

    func sessions(projectId: String) async throws -> [Session] {
        try checkOpen()
        return state.withLock { state in state.sessions.filter { $0.projectId == projectId } }
    }

    func createSession(projectId: String, size: TerminalGridSize) async throws -> Session {
        try checkOpen()
        return state.withLock { state in
            let session = Fixtures.session(state.nextSessionID, project: projectId)
            state.nextSessionID += 1
            state.sessions.append(session)
            state.created.append(size)
            return session
        }
    }

    func endSession(_ sessionId: UInt64) async throws {
        try checkOpen()
        state.withLock { state in
            state.ended.append(sessionId)
            state.sessions.removeAll { $0.id == sessionId }
        }
    }

    func attach(sessionId: UInt64, size: TerminalGridSize) async throws -> any ServerTerminalChannel {
        try checkOpen()
        return try state.withLock { state -> FakeTerminalChannel in
            if let failure = state.attachFailures.removeValue(forKey: sessionId) {
                throw failure
            }
            let channel = FakeTerminalChannel(sessionId: sessionId, screen: Fixtures.screen(title: "shell \(sessionId)"))
            state.channels[sessionId] = channel
            state.attached.append(sessionId)
            return channel
        }
    }

    func disconnect() {
        state.withLock { $0.isDisconnected = true }
    }

    private func checkOpen() throws {
        guard state.withLock({ $0.isDisconnected }) else { return }
        throw MobileError.Disconnected
    }
}

final class FakeServerConnector: ServerConnector {
    private let state: Mutex<State>

    struct State {
        var outcomes: [Result<FakeServerConnection, MobileError>]
        var handlers: [@Sendable (ConnectionEvent) -> Void] = []
    }

    init(outcomes: [Result<FakeServerConnection, MobileError>]) {
        state = Mutex(State(outcomes: outcomes))
    }

    var connectCount: Int {
        state.withLock { $0.handlers.count }
    }

    func connect(
        to credential: ServerCredential,
        events: @escaping @Sendable (ConnectionEvent) -> Void
    ) async throws -> any ServerConnection {
        let outcome = state.withLock { state -> Result<FakeServerConnection, MobileError> in
            state.handlers.append(events)
            return state.outcomes.isEmpty ? .failure(.Unreachable(reason: "no more outcomes")) : state.outcomes.removeFirst()
        }
        return try outcome.get()
    }

    func emit(_ event: ConnectionEvent, fromAttempt attempt: Int? = nil) {
        let handler = state.withLock { state in
            attempt.map { state.handlers[$0 - 1] } ?? state.handlers.last
        }
        handler?(event)
    }
}
