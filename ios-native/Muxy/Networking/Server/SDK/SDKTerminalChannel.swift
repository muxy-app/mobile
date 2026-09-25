import Foundation
import MuxyMobile

nonisolated final class SDKTerminalChannel: ServerTerminalChannel {
    private let terminal: MuxyMobile.Terminal
    private let lanes: SDKLanes

    init(terminal: MuxyMobile.Terminal, lanes: SDKLanes) {
        self.terminal = terminal
        self.lanes = lanes
    }

    var sessionId: UInt64 {
        terminal.sessionId()
    }

    func screen() -> Screen {
        terminal.screen()
    }

    func send(text: String) {
        lanes.send { [terminal] in
            try terminal.sendInput(bytes: Data(text.utf8))
        }
    }

    func send(key: Key, modifiers: Modifiers) {
        lanes.send { [terminal] in
            try terminal.sendKey(key: key, modifiers: modifiers)
        }
    }

    func paste(_ text: String) {
        lanes.send { [terminal] in
            try terminal.paste(text: text)
        }
    }

    func resize(to size: TerminalGridSize) async throws {
        try await lanes.request { [terminal] in
            try terminal.resize(columns: size.columns, rows: size.rows)
        }
    }

    func scrollback(maxRows: UInt16) async throws -> any ScrollbackSnapshot {
        let scrollback = try await lanes.request { [terminal] in
            try terminal.scrollback(maxRows: maxRows)
        }
        return SDKScrollbackSnapshot(scrollback: scrollback, lanes: lanes)
    }

    func detach() async throws {
        try await lanes.request { [terminal] in
            try terminal.detach()
        }
    }
}
