import Foundation
import Testing
@testable import Muxy

struct TerminalTextInputStateTests {
    @Test func typedTextIsSent() {
        var state = TerminalTextInputState()
        #expect(state.insert("ls") == [.text("ls")])
        #expect(state.text == "ls")
        #expect(state.selection == NSRange(location: 2, length: 0))
    }

    @Test func typingOverASelectionErasesItFirst() {
        var state = TerminalTextInputState()
        _ = state.insert("teh")
        state.select(NSRange(location: 1, length: 2))
        #expect(state.insert("he") == [.backspaces(2), .text("he")])
        #expect(state.text == "the")
    }

    @Test func compositionIsNotSentUntilCommitted() {
        var state = TerminalTextInputState()
        state.setMarkedText("ni", selectedRange: NSRange(location: 2, length: 0))
        #expect(state.markedText == "ni")
        #expect(state.insert("你") == [.text("你")])
        #expect(state.markedText == nil)
        #expect(state.text == "你")
    }

    @Test func unmarkingCommitsTheComposition() {
        var state = TerminalTextInputState()
        state.setMarkedText("かな", selectedRange: NSRange(location: 2, length: 0))
        #expect(state.unmarkText() == [.text("かな")])
        #expect(state.markedText == nil)
    }

    @Test func clearingTheCompositionSendsNothing() {
        var state = TerminalTextInputState()
        state.setMarkedText("n", selectedRange: NSRange(location: 1, length: 0))
        state.setMarkedText(nil, selectedRange: NSRange(location: 0, length: 0))
        #expect(state.markedText == nil)
        #expect(state.unmarkText().isEmpty)
    }

    @Test func deletingWithNothingBufferedStillSendsBackspace() {
        var state = TerminalTextInputState()
        #expect(state.deleteBackward() == [.backspaces(1)])
    }

    @Test func deletingRemovesAWholeEmoji() {
        var state = TerminalTextInputState()
        _ = state.insert("a👍🏽")
        #expect(state.deleteBackward() == [.backspaces(1)])
        #expect(state.text == "a")
    }

    @Test func replacingAWordSendsBackspacesThenTheNewWord() {
        var state = TerminalTextInputState()
        _ = state.insert("git stauts")
        #expect(state.replace(NSRange(location: 4, length: 6), with: "status") == [.backspaces(6), .text("status")])
        #expect(state.text == "git status")
    }

    @Test func replacingDuringCompositionIsIgnored() {
        var state = TerminalTextInputState()
        state.setMarkedText("ni", selectedRange: NSRange(location: 2, length: 0))
        #expect(state.replace(NSRange(location: 0, length: 2), with: "x").isEmpty)
    }

    @Test func aNewLineAsksForAFreshBuffer() {
        var state = TerminalTextInputState()
        _ = state.insert("ls")
        #expect(!state.needsReset)
        _ = state.insert("\n")
        #expect(state.needsReset)
        state.reset()
        #expect(state.length == 0)
    }

    @Test func outOfRangeSubstringsAreClamped() {
        var state = TerminalTextInputState()
        _ = state.insert("abc")
        #expect(state.substring(NSRange(location: 2, length: 10)) == "c")
        #expect(state.substring(NSRange(location: 10, length: 1)).isEmpty)
    }
}
