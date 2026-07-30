import Foundation

nonisolated enum TerminalArrow {
    case up
    case down
    case left
    case right
}

nonisolated enum TerminalInputEncoding {
    static let escape: [UInt8] = [0x1b]
    static let tab: [UInt8] = [0x09]
    static let enter: [UInt8] = [0x0d]
    static let backspace: [UInt8] = [0x7f]

    static func arrow(_ arrow: TerminalArrow, applicationCursor: Bool) -> [UInt8] {
        let prefix: [UInt8] = applicationCursor ? [0x1b, 0x4f] : [0x1b, 0x5b]
        return prefix + [letter(for: arrow)]
    }

    static func apply(_ modifier: TerminalModifier, to text: String) -> String? {
        switch modifier {
        case .ctrl:
            return control(text)
        case .shift:
            return text.uppercased()
        case .alt:
            return "\u{1b}" + text
        case .cmd:
            return text
        }
    }

    private static func control(_ text: String) -> String? {
        guard text.count == 1, let scalar = text.unicodeScalars.first else { return nil }
        let value = scalar.value
        switch value {
        case 0x40 ... 0x5f:
            return UnicodeScalar(value - 0x40).map(String.init)
        case 0x61 ... 0x7a:
            return UnicodeScalar(value - 0x60).map(String.init)
        case 0x20:
            return "\u{00}"
        default:
            return nil
        }
    }

    private static func letter(for arrow: TerminalArrow) -> UInt8 {
        switch arrow {
        case .up:
            return 0x41
        case .down:
            return 0x42
        case .right:
            return 0x43
        case .left:
            return 0x44
        }
    }
}
