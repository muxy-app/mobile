import Foundation

nonisolated struct ThemeTokens: Equatable, Sendable {
    let isDark: Bool
    let background: ThemeColor
    let secondaryBackground: ThemeColor
    let groupedBackground: ThemeColor
    let secondaryGroupedBackground: ThemeColor
    let separator: ThemeColor
    let foreground: ThemeColor
    let secondaryForeground: ThemeColor
    let accent: ThemeColor
    let onAccent: ThemeColor
    let red: ThemeColor
    let green: ThemeColor
    let yellow: ThemeColor
    let cyan: ThemeColor

    init(palette: ThemePalette) {
        let background = ThemeColor(rgb: palette.background)
        let text = ThemeColor(rgb: palette.foreground)
        let isDark = background.luminance < text.luminance
        let step = isDark ? ThemeTokens.darkStep : ThemeTokens.lightStep
        let raiseTarget: ThemeColor = isDark ? text : .white
        let recedeTarget: ThemeColor = isDark ? .black : text

        let lift = min(step.total * step.rowShare, abs(raiseTarget.lightness - background.lightness))
        let rows = background.shiftingLightness(by: lift, toward: raiseTarget)
        let recession = min(step.total - lift, abs(background.lightness - recedeTarget.lightness))
        let canvas = background.shiftingLightness(by: recession, toward: recedeTarget)

        let layers = [background, canvas, rows]
        let extreme: ThemeColor = isDark ? .white : .black
        let foreground = text.ensuringContrast(ThemeTokens.minimumContrast, against: layers, toward: [extreme])
        let legible: (ThemeColor) -> ThemeColor = {
            $0.ensuringContrast(ThemeTokens.minimumContrast, against: layers, toward: [foreground, extreme])
        }
        let accent = legible(ThemeTokens.ansiColor(at: 4, in: palette))

        self.isDark = isDark
        self.background = background
        secondaryBackground = isDark ? rows : canvas
        groupedBackground = canvas
        secondaryGroupedBackground = rows
        separator = rows.shiftingLightness(by: step.separator, toward: text)
        self.foreground = foreground
        secondaryForeground = background
            .mixed(with: foreground, amount: ThemeTokens.secondaryEmphasis)
            .ensuringContrast(ThemeTokens.minimumContrast, against: layers, toward: [foreground])
        self.accent = accent
        onAccent = ThemeTokens.onAccent(for: accent, background: background)
        red = legible(ThemeTokens.ansiColor(at: 1, in: palette))
        green = legible(ThemeTokens.ansiColor(at: 2, in: palette))
        yellow = legible(ThemeTokens.ansiColor(at: 3, in: palette))
        cyan = legible(ThemeTokens.ansiColor(at: 6, in: palette))
    }

    private static func onAccent(for accent: ThemeColor, background: ThemeColor) -> ThemeColor {
        guard accent.contrast(with: background) < minimumContrast else { return background }
        return accent.contrast(with: .white) >= accent.contrast(with: .black) ? .white : .black
    }

    private static func ansiColor(at index: Int, in palette: ThemePalette) -> ThemeColor {
        let source = palette.ansi.indices.contains(index) ? palette.ansi : ThemePalette.muxy.ansi
        return ThemeColor(rgb: source[index])
    }

    private struct LightnessStep {
        let total: Double
        let rowShare: Double
        let separator: Double
    }

    private static let darkStep = LightnessStep(total: 10, rowShare: 0.6, separator: 12)
    private static let lightStep = LightnessStep(total: 7, rowShare: 0.4, separator: 14)
    private static let minimumContrast = 4.5
    private static let secondaryEmphasis = 0.6
}
