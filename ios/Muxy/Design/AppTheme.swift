import SwiftUI

nonisolated struct AppTheme: Equatable, Sendable {
    let background: Color
    let secondaryBackground: Color
    let groupedBackground: Color
    let secondaryGroupedBackground: Color
    let separator: Color
    let foreground: Color
    let secondaryForeground: Color
    let accent: Color
    let onAccent: Color
    let red: Color
    let green: Color
    let yellow: Color
    let cyan: Color
    let isDark: Bool
    let terminalPalette: ThemePalette

    static let muxy = AppTheme(palette: .muxy)

    init(palette: ThemePalette) {
        self.init(tokens: ThemeTokens(palette: palette), terminalPalette: palette)
    }

    init(isDark: Bool, terminalPalette: ThemePalette) {
        let palette = ThemePalette(
            name: "Muxy Interface",
            foreground: isDark ? 0xF3F4F8 : 0x20232D,
            background: isDark ? 0x171A21 : 0xF4F5F8,
            ansi: ThemePalette.muxy.ansi,
            cursor: ThemePalette.muxy.cursor,
            cursorText: ThemePalette.muxy.cursorText,
            selectionBackground: ThemePalette.muxy.selectionBackground,
            selectionForeground: ThemePalette.muxy.selectionForeground
        )
        self.init(tokens: ThemeTokens(palette: palette), terminalPalette: terminalPalette, neutralAccent: true)
    }

    private init(tokens: ThemeTokens, terminalPalette: ThemePalette, neutralAccent: Bool = false) {
        background = tokens.background.color
        secondaryBackground = tokens.secondaryBackground.color
        groupedBackground = tokens.groupedBackground.color
        secondaryGroupedBackground = tokens.secondaryGroupedBackground.color
        separator = tokens.separator.color
        foreground = tokens.foreground.color
        secondaryForeground = tokens.secondaryForeground.color
        accent = neutralAccent ? tokens.foreground.color : tokens.accent.color
        onAccent = neutralAccent ? tokens.background.color : tokens.onAccent.color
        red = tokens.red.color
        green = tokens.green.color
        yellow = tokens.yellow.color
        cyan = tokens.cyan.color
        isDark = tokens.isDark
        self.terminalPalette = terminalPalette.replacingBackground(with: ThemeTokens(palette: terminalPalette).groupedBackground.rgb)
    }
}
