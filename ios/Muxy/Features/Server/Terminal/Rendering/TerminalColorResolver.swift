import Foundation
import MuxyMobile

nonisolated struct ResolvedCellStyle: Equatable, Sendable {
    let foreground: UInt32
    let background: UInt32
    let decoration: UInt32
    let drawsText: Bool
}

nonisolated struct TerminalColorResolver: Sendable {
    let theme: ThemePalette

    func resolve(_ style: Style) -> ResolvedCellStyle {
        var foreground = rgb(style.foreground, fallback: theme.foreground)
        var background = rgb(style.background, fallback: theme.background)
        if style.inverse {
            swap(&foreground, &background)
        }
        if style.faint {
            foreground = Self.blend(foreground, toward: background, amount: 0.5)
        }
        return ResolvedCellStyle(
            foreground: foreground,
            background: background,
            decoration: rgb(style.underlineColor, fallback: foreground),
            drawsText: !style.invisible
        )
    }

    func rgb(_ color: TerminalColor, fallback: UInt32) -> UInt32 {
        switch color {
        case .default:
            return fallback
        case let .indexed(index):
            return indexed(index, fallback: fallback)
        case let .rgb(red, green, blue):
            return Self.pack(UInt32(red), UInt32(green), UInt32(blue))
        }
    }

    private func indexed(_ index: UInt8, fallback: UInt32) -> UInt32 {
        guard index >= 16 else {
            let position = Int(index)
            return theme.ansi.indices.contains(position) ? theme.ansi[position] : fallback
        }
        return Self.xterm(index)
    }

    static func xterm(_ index: UInt8) -> UInt32 {
        guard index >= 232 else {
            let value = Int(index) - 16
            return pack(level(value / 36), level((value / 6) % 6), level(value % 6))
        }
        let gray = UInt32(8 + (Int(index) - 232) * 10)
        return pack(gray, gray, gray)
    }

    static func blend(_ color: UInt32, toward target: UInt32, amount: Double) -> UInt32 {
        func channel(_ shift: UInt32) -> UInt32 {
            let from = Double((color >> shift) & 0xFF)
            let to = Double((target >> shift) & 0xFF)
            return UInt32((from + (to - from) * amount).rounded())
        }
        return pack(channel(16), channel(8), channel(0))
    }

    private static func level(_ component: Int) -> UInt32 {
        component == 0 ? 0 : UInt32(55 + component * 40)
    }

    private static func pack(_ red: UInt32, _ green: UInt32, _ blue: UInt32) -> UInt32 {
        (red << 16) | (green << 8) | blue
    }
}
