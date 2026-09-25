import SwiftTerm
import UIKit

nonisolated struct TerminalTheme: Equatable {
    let foreground: UIColor
    let background: UIColor
    let palette: [SwiftTerm.Color]

    init(clientTheme: ClientTerminalTheme) {
        foreground = TerminalTheme.uiColor(fromRGB: clientTheme.fg)
        background = TerminalTheme.uiColor(fromRGB: clientTheme.bg)
        palette = clientTheme.palette.map(TerminalTheme.terminalColor(fromRGB:))
    }

    static func uiColor(fromRGB rgb: UInt32) -> UIColor {
        UIColor(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }

    static func terminalColor(fromRGB rgb: UInt32) -> SwiftTerm.Color {
        SwiftTerm.Color(
            red: channel16(UInt8((rgb >> 16) & 0xFF)),
            green: channel16(UInt8((rgb >> 8) & 0xFF)),
            blue: channel16(UInt8(rgb & 0xFF))
        )
    }

    private static func channel16(_ value: UInt8) -> UInt16 {
        UInt16(value) * 257
    }
}

extension ClientTerminalTheme {
    init(palette: ThemePalette) {
        self.init(
            fg: palette.foreground,
            bg: palette.background,
            palette: palette.ansi,
            cursorColor: palette.cursor,
            cursorText: palette.cursorText,
            selectionBackground: palette.selectionBackground,
            selectionForeground: palette.selectionForeground
        )
    }
}
