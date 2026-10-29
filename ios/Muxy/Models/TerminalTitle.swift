import Foundation

nonisolated enum TerminalTitle {
    static func sanitized(_ title: String) -> String {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.unicodeScalars.contains(where: { !$0.isASCII }) else { return trimmed }
        return String(trimmed.filter { !isDecoration($0) })
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func isDecoration(_ character: Character) -> Bool {
        let scalars = character.unicodeScalars
        if scalars.allSatisfy(isInvisibleModifier) { return true }
        return scalars.contains(where: isDecorationScalar)
    }

    private static func isDecorationScalar(_ scalar: Unicode.Scalar) -> Bool {
        switch scalar.properties.generalCategory {
        case .otherSymbol, .privateUse: return true
        default: break
        }
        return scalar.properties.isEmojiPresentation || scalar.value == 0x20E3 || scalar.value == 0xFE0F
    }

    private static func isInvisibleModifier(_ scalar: Unicode.Scalar) -> Bool {
        switch scalar.value {
        case 0x200D, 0xFE0E, 0xFE0F, 0xE0020 ... 0xE007F: true
        default: false
        }
    }
}
