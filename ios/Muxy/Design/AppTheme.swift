import SwiftUI

nonisolated struct AppTheme: Equatable, Sendable {
    let background: Color
    let surface: Color
    let foreground: Color
    let secondaryForeground: Color
    let accent: Color
    let onAccent: Color
    let separator: Color
    let selectionBackground: Color
    let selectionForeground: Color
    let isDark: Bool
    let event: DeviceThemeEvent

    static let muxy = AppTheme(event: DeviceThemeEvent.muxy)

    init(event: DeviceThemeEvent) {
        self.event = event
        let background = AppColor(rgb: event.bg)
        let foreground = AppColor(rgb: event.fg)
        let palette = event.palette ?? []
        let accent = AppTheme.accentColor(from: palette)

        self.background = background.color
        surface = background.elevated.color
        self.foreground = foreground.color
        secondaryForeground = foreground.color.opacity(AppTheme.secondaryOpacity)
        self.accent = accent.color
        onAccent = accent.isDark ? .white : .black
        separator = foreground.color.opacity(AppTheme.separatorOpacity)
        selectionBackground = accent.color
        selectionForeground = background.color
        isDark = background.isDark
    }

    private static func accentColor(from palette: [UInt32]) -> AppColor {
        guard palette.indices.contains(AppTheme.accentIndex) else { return AppColor(rgb: AppTheme.brandRGB) }
        return AppColor(rgb: palette[AppTheme.accentIndex])
    }

    private static let brandRGB: UInt32 = 0xA74BA7

    private static let accentIndex = 4
    private static let secondaryOpacity = 0.65
    private static let separatorOpacity = 0.18
}

extension DeviceThemeEvent {
    static let muxy = DeviceThemeEvent(
        fg: 0xC9C2D9,
        bg: 0x19171F,
        palette: [
            0x141219, 0xEC4899, 0x34D399, 0xE0AF68,
            0xC370D3, 0x6366F1, 0x22D3EE, 0xA9B1D6,
            0x2E2B34, 0xF472B6, 0x6EE7B7, 0xFBBF24,
            0xD99BE5, 0x818CF8, 0x67E8F9, 0xC9C2D9
        ]
    )
}

private struct AppColor {
    let red: Double
    let green: Double
    let blue: Double

    init(rgb: UInt32) {
        red = Double((rgb >> 16) & 0xFF) / 255
        green = Double((rgb >> 8) & 0xFF) / 255
        blue = Double(rgb & 0xFF) / 255
    }

    private init(red: Double, green: Double, blue: Double) {
        self.red = red
        self.green = green
        self.blue = blue
    }

    var color: Color {
        Color(.sRGB, red: red, green: green, blue: blue)
    }

    var isDark: Bool {
        let luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
        return luminance < 0.5
    }

    var elevated: AppColor {
        let amount = isDark ? AppColor.lightenAmount : -AppColor.lightenAmount
        return AppColor(
            red: shift(red, by: amount),
            green: shift(green, by: amount),
            blue: shift(blue, by: amount)
        )
    }

    private func shift(_ value: Double, by amount: Double) -> Double {
        min(1, max(0, value + amount))
    }

    private static let lightenAmount = 0.09
}
