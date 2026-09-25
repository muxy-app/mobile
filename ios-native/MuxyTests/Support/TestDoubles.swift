import Foundation
import MuxyMobile
import Synchronization
@testable import Muxy

enum Fixtures {
    static let link = "muxy://pair?v=1&h=192.168.1.20&h=studio.local&p=7419&f=ab&s=cd"

    static func credential(id: String = "server-1", name: String = "Studio") -> ServerCredential {
        ServerCredential(
            serverId: id,
            serverName: name,
            hosts: ["192.168.1.20", "studio.local"],
            port: 7419,
            fingerprint: Data(repeating: 7, count: 32),
            deviceId: "3f2a0000-0000-4000-8000-000000000000",
            token: Data(repeating: 9, count: 32)
        )
    }

    static func style(_ change: (inout Style) -> Void = { _ in }) -> Style {
        var style = Style(
            foreground: .default,
            background: .default,
            bold: false,
            italic: false,
            faint: false,
            underline: .none,
            underlineColor: .default,
            strikethrough: false,
            overline: false,
            inverse: false,
            invisible: false
        )
        change(&style)
        return style
    }

    static func line(_ text: String) -> Line {
        Line(spans: [Span(text: text, width: UInt16(text.count), style: style())])
    }

    static func project(
        id: String,
        name: String,
        parentId: String? = nil,
        isHome: Bool = false,
        isWorktree: Bool = false
    ) -> ServerProject {
        ServerProject(
            id: id,
            name: name,
            directory: "/Users/demo/\(name)",
            color: "#C370D3",
            icon: nil,
            logo: nil,
            parentId: parentId,
            isHome: isHome,
            isWorktree: isWorktree
        )
    }
}

struct StubPairingService: ServerPairingService {
    var parsed: Result<PairingLink, MobileError>
    var paired: Result<ServerCredential, MobileError>

    func parse(_ link: String) throws -> PairingLink {
        try parsed.get()
    }

    func pair(link: String, deviceName: String) async throws -> ServerCredential {
        try paired.get()
    }
}

final class InMemoryCredentialStore: CredentialStore {
    private let items = Mutex<[String: ServerCredential]>([:])
    private let failsSaving: Bool

    init(failsSaving: Bool = false) {
        self.failsSaving = failsSaving
    }

    func all() throws -> [ServerCredential] {
        items.withLock { Array($0.values) }
    }

    func credential(serverId: String) throws -> ServerCredential? {
        items.withLock { $0[serverId] }
    }

    func save(_ credential: ServerCredential) throws {
        guard !failsSaving else { throw KeychainError.unexpectedStatus(-1) }
        items.withLock { $0[credential.serverId] = credential }
    }

    func delete(serverId: String) throws {
        _ = items.withLock { $0.removeValue(forKey: serverId) }
    }
}

final class StubScrollbackSnapshot: ScrollbackSnapshot {
    let historyRows: UInt64

    private let initial: [Line]
    private let pages: Mutex<[Result<[Line], MobileError>]>
    private let requests = Mutex(0)

    init(lines: [Line], historyRows: UInt64, olderPages: [Result<[Line], MobileError>]) {
        initial = lines
        self.historyRows = historyRows
        pages = Mutex(olderPages)
    }

    var loadCount: Int {
        requests.withLock { $0 }
    }

    func lines() -> [Line] {
        initial
    }

    func loadOlder(maxRows: UInt16) async throws -> [Line] {
        requests.withLock { $0 += 1 }
        let next = pages.withLock { pages -> Result<[Line], MobileError> in
            pages.isEmpty ? .success([]) : pages.removeFirst()
        }
        return try next.get()
    }
}
