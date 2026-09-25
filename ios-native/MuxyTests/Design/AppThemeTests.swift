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

    @Test func surfaceElevatesAboveBackground() {
        let (bgRed, _, _) = rgb(AppTheme.muxy.background)
        let (surfaceRed, _, _) = rgb(AppTheme.muxy.surface)
        #expect(surfaceRed > bgRed)
    }

    @Test func surfaceDarkensBelowLightBackground() {
        let theme = AppTheme(palette: palette(fg: 0x000000, bg: 0xFFFFFF))
        let (surfaceRed, _, _) = rgb(theme.surface)
        #expect(surfaceRed < 1)
    }

    @Test func selectionBackgroundMatchesAccent() {
        expectColor(AppTheme.muxy.selectionBackground, equals: 0xC370D3)
    }

    @Test func selectionForegroundMatchesBackground() {
        expectColor(AppTheme.muxy.selectionForeground, equals: 0x19171F)
    }

    @Test func lightBackgroundIsNotDark() {
        let theme = AppTheme(palette: palette(fg: 0x000000, bg: 0xFFFFFF))
        #expect(!theme.isDark)
    }

    @Test func darkBackgroundIsDark() {
        let theme = AppTheme(palette: palette(fg: 0xFFFFFF, bg: 0x000000))
        #expect(theme.isDark)
    }

    @Test func accentFallsBackToBrandWhenPaletteMissing() {
        let theme = AppTheme(palette: palette(fg: 0xFFFFFF, bg: 0x000000))
        expectColor(theme.accent, equals: 0xA74BA7)
    }

    @Test func surfaceLightensAboveDarkBackground() {
        let theme = AppTheme(palette: palette(fg: 0xFFFFFF, bg: 0x000000))
        let (red, green, blue) = rgb(theme.surface)
        #expect(red > 0.02)
        #expect(green > 0.02)
        #expect(blue > 0.02)
    }

    @Test func onAccentContrastsWithAccent() {
        let darkAccent = AppTheme(palette: palette(fg: 0xFFFFFF, bg: 0x000000, ansi: ansiWithAccent(0x101010)))
        let lightAccent = AppTheme(palette: palette(fg: 0xFFFFFF, bg: 0x000000, ansi: ansiWithAccent(0xF0F0F0)))
        expectColor(darkAccent.onAccent, equals: 0xFFFFFF)
        expectColor(lightAccent.onAccent, equals: 0x000000)
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

    private func ansiWithAccent(_ accent: UInt32) -> [UInt32] {
        var ansi = Array(repeating: UInt32(0), count: 16)
        ansi[4] = accent
        return ansi
    }
}
