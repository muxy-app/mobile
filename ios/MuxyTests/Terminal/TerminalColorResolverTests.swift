import MuxyMobile
import Testing
@testable import Muxy

struct TerminalColorResolverTests {
    private let resolver = TerminalColorResolver(theme: .muxy)

    @Test func defaultColorsComeFromTheTheme() {
        let resolved = resolver.resolve(Fixtures.style())
        #expect(resolved.foreground == ThemePalette.muxy.foreground)
        #expect(resolved.background == ThemePalette.muxy.background)
        #expect(resolved.drawsText)
    }

    @Test func theFirstSixteenColorsComeFromTheTheme() {
        #expect(resolver.rgb(.indexed(index: 1), fallback: 0) == ThemePalette.muxy.ansi[1])
        #expect(resolver.rgb(.indexed(index: 15), fallback: 0) == ThemePalette.muxy.ansi[15])
    }

    @Test(arguments: [
        (UInt8(16), UInt32(0x000000)),
        (UInt8(21), UInt32(0x0000FF)),
        (UInt8(196), UInt32(0xFF0000)),
        (UInt8(231), UInt32(0xFFFFFF)),
        (UInt8(232), UInt32(0x080808)),
        (UInt8(255), UInt32(0xEEEEEE)),
    ])
    func extendedColorsFollowTheXtermPalette(index: UInt8, rgb: UInt32) {
        #expect(resolver.rgb(.indexed(index: index), fallback: 0) == rgb)
    }

    @Test func rgbColorsAreExact() {
        #expect(resolver.rgb(.rgb(red: 0x12, green: 0x34, blue: 0x56), fallback: 0) == 0x123456)
    }

    @Test func aShortPaletteFallsBack() {
        let resolver = TerminalColorResolver(theme: ThemePalette(
            name: "Short",
            foreground: 0xFFFFFF,
            background: 0x000000,
            ansi: [],
            cursor: 0xFFFFFF,
            cursorText: 0x000000,
            selectionBackground: 0xFFFFFF,
            selectionForeground: 0x000000
        ))
        #expect(resolver.rgb(.indexed(index: 3), fallback: 0xABCDEF) == 0xABCDEF)
    }

    @Test func inverseSwapsIncludingDefaults() {
        let resolved = resolver.resolve(Fixtures.style { $0.inverse = true })
        #expect(resolved.foreground == ThemePalette.muxy.background)
        #expect(resolved.background == ThemePalette.muxy.foreground)
    }

    @Test func faintBlendsHalfwayTowardTheBackground() {
        let style = Fixtures.style {
            $0.foreground = .rgb(red: 255, green: 255, blue: 255)
            $0.background = .rgb(red: 0, green: 0, blue: 0)
            $0.faint = true
        }
        #expect(resolver.resolve(style).foreground == 0x808080)
    }

    @Test func invisibleTextDrawsOnlyTheBackground() {
        #expect(!resolver.resolve(Fixtures.style { $0.invisible = true }).drawsText)
    }

    @Test func underlinesUseTheirOwnColorOrTheForeground() {
        let plain = resolver.resolve(Fixtures.style { $0.foreground = .rgb(red: 1, green: 2, blue: 3) })
        #expect(plain.decoration == 0x010203)

        let colored = resolver.resolve(Fixtures.style { $0.underlineColor = .rgb(red: 9, green: 9, blue: 9) })
        #expect(colored.decoration == 0x090909)
    }
}
