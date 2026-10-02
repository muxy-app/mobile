import SwiftUI
import Testing
@testable import Muxy

struct AppThemeTests {
    private func rgb(_ color: Color) -> (Double, Double, Double) {
        let resolved = color.resolve(in: EnvironmentValues())
        return (Double(resolved.red), Double(resolved.green), Double(resolved.blue))
    }

    private func expectColor(_ color: Color, equals rgb: UInt32) {
        let (red, green, blue) = self.rgb(color)
        #expect(abs(red - Double((rgb >> 16) & 0xFF) / 255) < 0.01)
        #expect(abs(green - Double((rgb >> 8) & 0xFF) / 255) < 0.01)
        #expect(abs(blue - Double(rgb & 0xFF) / 255) < 0.01)
    }

    @Test func defaultMuxyThemeIsDark() {
        #expect(AppTheme.muxy.isDark)
    }

    @Test func defaultMuxyThemeUsesMuxyBackgroundAndForeground() {
        expectColor(AppTheme.muxy.background, equals: 0x19171F)
        expectColor(AppTheme.muxy.foreground, equals: 0xC9C2D9)
    }

    @Test func accentUsesPaletteIndexFour() {
        expectColor(AppTheme.muxy.accent, equals: 0xC370D3)
    }

    @Test func lightBackgroundIsNotDark() {
        let theme = AppTheme(palette: palette(fg: 0x000000, bg: 0xFFFFFF))
        #expect(!theme.isDark)
    }

    @Test func darkBackgroundIsDark() {
        let theme = AppTheme(palette: palette(fg: 0xFFFFFF, bg: 0x000000))
        #expect(theme.isDark)
    }

    @Test func accentFallsBackToMuxyPaletteWhenMissing() {
        let theme = AppTheme(palette: palette(fg: 0xFFFFFF, bg: 0x000000))
        expectColor(theme.accent, equals: 0xC370D3)
    }

    private func palette(fg: UInt32, bg: UInt32, ansi: [UInt32] = []) -> ThemePalette {
        ThemePalette(
            name: "Test",
            foreground: fg,
            background: bg,
            ansi: ansi,
            cursor: fg,
            cursorText: bg,
            selectionBackground: fg,
            selectionForeground: bg
        )
    }
}
