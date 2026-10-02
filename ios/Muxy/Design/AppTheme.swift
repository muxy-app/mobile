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
        let tokens = ThemeTokens(palette: palette)
        background = tokens.background.color
        secondaryBackground = tokens.secondaryBackground.color
        groupedBackground = tokens.groupedBackground.color
        secondaryGroupedBackground = tokens.secondaryGroupedBackground.color
        separator = tokens.separator.color
        foreground = tokens.foreground.color
        secondaryForeground = tokens.secondaryForeground.color
        accent = tokens.accent.color
        onAccent = tokens.onAccent.color
        red = tokens.red.color
        green = tokens.green.color
        yellow = tokens.yellow.color
        cyan = tokens.cyan.color
        isDark = tokens.isDark
        terminalPalette = palette.replacingBackground(with: tokens.groupedBackground.rgb)
    }
}
