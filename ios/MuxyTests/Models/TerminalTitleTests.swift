import Foundation
import Testing
@testable import Muxy

struct TerminalTitleTests {
    @Test(arguments: [
        ("✳ Fix pairing timeout", "Fix pairing timeout"),
        ("⠐ Working on tests", "Working on tests"),
        ("🤖 build", "build"),
        ("1️⃣ first step", "first step"),
        ("👨‍👩‍👧 family", "family"),
        ("deploy 🚀 now", "deploy  now"),
    ])
    func stripsDecorations(input: String, expected: String) {
        #expect(TerminalTitle.sanitized(input) == expected)
    }

    @Test(arguments: ["zsh", "npm run dev", "café au lait", "xal — $code-review pr 1248"])
    func keepsPlainText(input: String) {
        #expect(TerminalTitle.sanitized(input) == input)
    }

    @Test func trimsWhitespace() {
        #expect(TerminalTitle.sanitized("  vim  ") == "vim")
    }

    @Test func decorationOnlyTitlesBecomeEmpty() {
        #expect(TerminalTitle.sanitized("✳ ⠂") == "")
    }

    @Test func workspaceTabsFallBackToTerminal() {
        let decorated = Tab(id: UUID(), kind: .terminal, title: "🤖 build", isPinned: false, paneID: nil)
        let empty = Tab(id: UUID(), kind: .terminal, title: "✳", isPinned: false, paneID: nil)

        #expect(decorated.displayTitle == "build")
        #expect(empty.displayTitle == "Terminal")
    }
}
