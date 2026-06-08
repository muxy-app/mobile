import SwiftTerm
import Testing
import UIKit
@testable import Muxy

struct TerminalThemeTests {
    @Test func defaultBackgroundTracksSystemColorScheme() {
        assertEqual(
            TerminalTheme.default.background,
            .systemBackground,
            style: .light
        )
        assertEqual(
            TerminalTheme.default.background,
            .systemBackground,
            style: .dark
        )
    }

    @Test func uiColorFromRGBExtractsChannels() {
        let color = TerminalTheme.uiColor(fromRGB: 0xFF8040)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        color.getRed(&red, green: &green, blue: &blue, alpha: nil)
        #expect(Int((red * 255).rounded()) == 0xFF)
        #expect(Int((green * 255).rounded()) == 0x80)
        #expect(Int((blue * 255).rounded()) == 0x40)
    }

    @Test func terminalColorScalesTo16Bit() {
        let color = TerminalTheme.terminalColor(fromRGB: 0xFF0000)
        #expect(color.red == 65535)
        #expect(color.green == 0)
        #expect(color.blue == 0)
    }

    @Test func terminalColorMidValueScales() {
        let color = TerminalTheme.terminalColor(fromRGB: 0x008000)
        #expect(color.green == UInt16(0x80) * 257)
    }

    @Test func themeFromEventBuildsPalette() {
        let event = DeviceThemeEvent(fg: 0xFFFFFF, bg: 0x000000, palette: [0xFF0000, 0x00FF00])
        let theme = TerminalTheme(event: event)
        #expect(theme.palette.count == 2)
        #expect(theme.palette[0] == SwiftTerm.Color(red: 65535, green: 0, blue: 0))
        #expect(theme.palette[1] == SwiftTerm.Color(red: 0, green: 65535, blue: 0))
    }

    @Test func themeFromEventUsesAppBackground() {
        let event = DeviceThemeEvent(fg: 0xFFFFFF, bg: 0x123456, palette: nil)
        let theme = TerminalTheme(event: event)
        assertEqual(theme.background, .systemBackground, style: .light)
        assertEqual(theme.background, .systemBackground, style: .dark)
    }

    @Test func themeWithoutPaletteIsEmpty() {
        let event = DeviceThemeEvent(fg: 0xFFFFFF, bg: 0x000000, palette: nil)
        let theme = TerminalTheme(event: event)
        #expect(theme.palette.isEmpty)
    }

    @Test func darkClientThemeMatchesAppTerminalPalette() {
        let theme = ClientTerminalTheme.dark
        #expect(theme.bg == 0x000000)
        #expect(theme.fg == 0xF2F2F7)
        #expect(theme.palette.count == 16)
        #expect(theme.palette[1] == 0xFF453A)
        #expect(theme.palette[4] == 0x0A84FF)
        #expect(theme.cursorColor == 0xF2F2F7)
        #expect(theme.selectionBackground == 0x2C2C2E)
    }

    @Test func lightClientThemeHasSixteenColorPalette() {
        let theme = ClientTerminalTheme.light
        #expect(theme.bg == 0xFFFFFF)
        #expect(theme.fg == 0x000000)
        #expect(theme.palette.count == 16)
        #expect(theme.palette[4] == 0x007AFF)
        #expect(theme.palette[1] == 0xFF3B30)
        #expect(theme.cursorText == 0xFFFFFF)
    }

    @Test func terminalThemeCanBeBuiltFromClientTheme() {
        let theme = TerminalTheme(clientTheme: .light)
        #expect(theme.palette.count == 16)
        assertEqual(theme.background, TerminalTheme.uiColor(fromRGB: 0xFFFFFF), style: .light)
        assertEqual(theme.foreground, TerminalTheme.uiColor(fromRGB: 0x000000), style: .light)
    }

    private func assertEqual(_ color: UIColor, _ expected: UIColor, style: UIUserInterfaceStyle) {
        let traits = UITraitCollection(userInterfaceStyle: style)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        var expectedRed: CGFloat = 0
        var expectedGreen: CGFloat = 0
        var expectedBlue: CGFloat = 0
        var expectedAlpha: CGFloat = 0

        color.resolvedColor(with: traits).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        expected.resolvedColor(with: traits).getRed(
            &expectedRed,
            green: &expectedGreen,
            blue: &expectedBlue,
            alpha: &expectedAlpha
        )

        #expect(red == expectedRed)
        #expect(green == expectedGreen)
        #expect(blue == expectedBlue)
        #expect(alpha == expectedAlpha)
    }
}
