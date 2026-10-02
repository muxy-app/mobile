import SwiftTerm
import Testing
import UIKit
@testable import Muxy

struct TerminalThemeTests {
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

    @Test func themeFromClientThemeBuildsPalette() {
        let theme = TerminalTheme(clientTheme: ClientTerminalTheme(palette: .muxy))
        #expect(theme.palette.count == 16)
        #expect(theme.palette[1] == TerminalTheme.terminalColor(fromRGB: 0xEC4899))
    }

    @Test func clientThemeFromPaletteUsesThemeCursorAndSelection() {
        let palette = ThemePalette.catppuccinMocha
        let clientTheme = ClientTerminalTheme(palette: palette)
        #expect(clientTheme.fg == palette.foreground)
        #expect(clientTheme.bg == palette.background)
        #expect(clientTheme.palette == palette.ansi)
        #expect(clientTheme.cursorColor == palette.cursor)
        #expect(clientTheme.cursorText == palette.cursorText)
        #expect(clientTheme.selectionBackground == palette.selectionBackground)
        #expect(clientTheme.selectionForeground == palette.selectionForeground)
    }

    @Test func terminalThemeCanBeBuiltFromClientTheme() {
        let theme = TerminalTheme(clientTheme: ClientTerminalTheme(palette: .muxyLight))
        #expect(theme.palette.count == 16)
        assertEqual(theme.background, TerminalTheme.uiColor(fromRGB: 0xF0F0F5), style: .light)
        assertEqual(theme.foreground, TerminalTheme.uiColor(fromRGB: 0x1E1E2E), style: .light)
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
