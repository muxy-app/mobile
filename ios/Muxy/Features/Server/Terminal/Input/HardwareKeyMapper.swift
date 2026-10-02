import UIKit

nonisolated enum HardwareKeyAction: Equatable, Sendable {
    case stroke(TerminalKeyStroke)
    case paste
    case copy
}

nonisolated enum HardwareKeyMapper {
    static let repeatableKeys: Set<TerminalKey> = [.up, .down, .left, .right, .delete, .pageUp, .pageDown]

    private static let specialKeys: [UIKeyboardHIDUsage: TerminalKey] = [
        .keyboardEscape: .escape,
        .keyboardUpArrow: .up,
        .keyboardDownArrow: .down,
        .keyboardLeftArrow: .left,
        .keyboardRightArrow: .right,
        .keyboardHome: .home,
        .keyboardEnd: .end,
        .keyboardPageUp: .pageUp,
        .keyboardPageDown: .pageDown,
        .keyboardDeleteForward: .delete,
        .keyboardInsert: .insert,
        .keyboardF1: .function(1),
        .keyboardF2: .function(2),
        .keyboardF3: .function(3),
        .keyboardF4: .function(4),
        .keyboardF5: .function(5),
        .keyboardF6: .function(6),
        .keyboardF7: .function(7),
        .keyboardF8: .function(8),
        .keyboardF9: .function(9),
        .keyboardF10: .function(10),
        .keyboardF11: .function(11),
        .keyboardF12: .function(12),
    ]

    private static let commandActions: [String: HardwareKeyAction] = [
        "v": .paste,
        "c": .copy,
    ]

    static func action(
        keyCode: UIKeyboardHIDUsage,
        modifierFlags: UIKeyModifierFlags,
        charactersIgnoringModifiers: String
    ) -> HardwareKeyAction? {
        if modifierFlags.contains(.command) {
            return commandActions[charactersIgnoringModifiers.lowercased()]
        }
        let modifiers = terminalModifiers(from: modifierFlags)
        if keyCode == .keyboardTab {
            let key: TerminalKey = modifiers.contains(.shift) ? .backTab : .tab
            return .stroke(TerminalKeyStroke(key, modifiers: modifiers.subtracting(.shift)))
        }
        if let key = specialKeys[keyCode] {
            return .stroke(TerminalKeyStroke(key, modifiers: modifiers))
        }
        guard modifiers.contains(.control), charactersIgnoringModifiers.count == 1 else { return nil }
        return .stroke(TerminalKeyStroke(.character(charactersIgnoringModifiers), modifiers: modifiers))
    }

    private static func terminalModifiers(from flags: UIKeyModifierFlags) -> TerminalKeyModifiers {
        var modifiers: TerminalKeyModifiers = []
        if flags.contains(.shift) {
            modifiers.insert(.shift)
        }
        if flags.contains(.alternate) {
            modifiers.insert(.alt)
        }
        if flags.contains(.control) {
            modifiers.insert(.control)
        }
        return modifiers
    }
}
