import Foundation
import MuxyMobile

extension TerminalKeyStroke {
    nonisolated var sdkKey: Key {
        switch key {
        case let .character(text):
            return .character(text: text)
        case .enter:
            return .enter
        case .tab:
            return .tab
        case .backTab:
            return .backTab
        case .escape:
            return .escape
        case .backspace:
            return .backspace
        case .insert:
            return .insert
        case .delete:
            return .delete
        case .up:
            return .up
        case .down:
            return .down
        case .left:
            return .left
        case .right:
            return .right
        case .home:
            return .home
        case .end:
            return .end
        case .pageUp:
            return .pageUp
        case .pageDown:
            return .pageDown
        case let .function(number):
            return .function(number: number)
        }
    }

    nonisolated var sdkModifiers: Modifiers {
        Modifiers(
            shift: modifiers.contains(.shift),
            alt: modifiers.contains(.alt),
            control: modifiers.contains(.control)
        )
    }
}
