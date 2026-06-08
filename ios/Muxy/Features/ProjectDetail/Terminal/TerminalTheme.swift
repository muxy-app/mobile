import SwiftTerm
import UIKit

nonisolated struct TerminalTheme: Equatable {
    let foreground: UIColor
    let background: UIColor
    let palette: [SwiftTerm.Color]

    static let `default` = TerminalTheme(
        foreground: .label,
        background: .systemBackground,
        palette: []
    )

    init(foreground: UIColor, background: UIColor, palette: [SwiftTerm.Color]) {
        self.foreground = foreground
        self.background = background
        self.palette = palette
    }

    init(event: DeviceThemeEvent) {
        foreground = TerminalTheme.uiColor(fromRGB: event.fg)
        background = .systemBackground
        palette = (event.palette ?? []).map(TerminalTheme.terminalColor(fromRGB:))
    }

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
    static let dark = ClientTerminalTheme(
        fg: 0xF2F2F7,
        bg: 0x000000,
        palette: [
            0x1C1C1E,
            0xFF453A,
            0x30D158,
            0xFFD60A,
            0x0A84FF,
            0xBF5AF2,
            0x64D2FF,
            0xF2F2F7,
            0x636366,
            0xFF6961,
            0x32D74B,
            0xFFE55C,
            0x5AC8FA,
            0xDA8FFF,
            0x5DE6FF,
            0xFFFFFF
        ],
        cursorColor: 0xF2F2F7,
        cursorText: 0x000000,
        selectionBackground: 0x2C2C2E,
        selectionForeground: 0xFFFFFF
    )

    static let light = ClientTerminalTheme(
        fg: 0x000000,
        bg: 0xFFFFFF,
        palette: [
            0x1C1C1E,
            0xFF3B30,
            0x34C759,
            0xFFCC00,
            0x007AFF,
            0xAF52DE,
            0x32ADE6,
            0xF2F2F7,
            0x8E8E93,
            0xFF453A,
            0x30D158,
            0xFFD60A,
            0x3395FF,
            0xBF5AF2,
            0x64D2FF,
            0xFFFFFF
        ],
        cursorColor: 0x000000,
        cursorText: 0xFFFFFF,
        selectionBackground: 0xD9E8FF,
        selectionForeground: 0x000000
    )
}
