import Testing
import UIKit
@testable import Muxy

struct HardwareKeyMapperTests {
    private func action(_ keyCode: UIKeyboardHIDUsage, _ flags: UIKeyModifierFlags = [], _ characters: String = "") -> HardwareKeyAction? {
        HardwareKeyMapper.action(keyCode: keyCode, modifierFlags: flags, charactersIgnoringModifiers: characters)
    }

    @Test func arrowsAndNavigationKeysAreHandled() {
        #expect(action(.keyboardUpArrow) == .stroke(TerminalKeyStroke(.up)))
        #expect(action(.keyboardPageDown) == .stroke(TerminalKeyStroke(.pageDown)))
        #expect(action(.keyboardDeleteForward) == .stroke(TerminalKeyStroke(.delete)))
        #expect(action(.keyboardEscape) == .stroke(TerminalKeyStroke(.escape)))
    }

    @Test func modifiersTravelWithSpecialKeys() {
        #expect(action(.keyboardLeftArrow, [.control, .shift]) == .stroke(TerminalKeyStroke(.left, modifiers: [.control, .shift])))
        #expect(action(.keyboardRightArrow, .alternate) == .stroke(TerminalKeyStroke(.right, modifiers: .alt)))
    }

    @Test func shiftTabBecomesBackTab() {
        #expect(action(.keyboardTab) == .stroke(TerminalKeyStroke(.tab)))
        #expect(action(.keyboardTab, .shift) == .stroke(TerminalKeyStroke(.backTab)))
    }

    @Test func functionKeysAreNumbered() {
        #expect(action(.keyboardF1) == .stroke(TerminalKeyStroke(.function(1))))
        #expect(action(.keyboardF12) == .stroke(TerminalKeyStroke(.function(12))))
    }

    @Test func controlLettersAreSentAsKeys() {
        #expect(action(.keyboardC, .control, "c") == .stroke(TerminalKeyStroke(.character("c"), modifiers: .control)))
    }

    @Test func commandShortcutsPasteAndCopy() {
        #expect(action(.keyboardV, .command, "v") == .paste)
        #expect(action(.keyboardC, .command, "c") == .copy)
        #expect(action(.keyboardX, .command, "x") == nil)
    }

    @Test func plainAndOptionTypingIsLeftToTheKeyboard() {
        #expect(action(.keyboardA, [], "a") == nil)
        #expect(action(.keyboardA, .alternate, "a") == nil)
        #expect(action(.keyboardReturnOrEnter, [], "\r") == nil)
    }

    @Test func onlyNavigationKeysRepeat() {
        #expect(HardwareKeyMapper.repeatableKeys.contains(.up))
        #expect(HardwareKeyMapper.repeatableKeys.contains(.delete))
        #expect(!HardwareKeyMapper.repeatableKeys.contains(.escape))
        #expect(!HardwareKeyMapper.repeatableKeys.contains(.tab))
    }
}
