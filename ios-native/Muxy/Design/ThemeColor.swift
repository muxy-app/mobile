import Foundation
import SwiftUI

nonisolated struct ThemeColor: Equatable, Sendable {
    let red: Double
    let green: Double
    let blue: Double
    let luminance: Double

    static let white = ThemeColor(rgb: 0xFFFFFF)
    static let black = ThemeColor(rgb: 0x000000)

    init(rgb: UInt32) {
        self.init(
            channels: Double((rgb >> 16) & 0xFF) / 255,
            Double((rgb >> 8) & 0xFF) / 255,
            Double(rgb & 0xFF) / 255
        )
    }

    private init(channels red: Double, _ green: Double, _ blue: Double) {
        self.red = red
        self.green = green
        self.blue = blue
        luminance = 0.2126 * ThemeColor.linearized(red)
            + 0.7152 * ThemeColor.linearized(green)
            + 0.0722 * ThemeColor.linearized(blue)
    }

    var rgb: UInt32 {
        ThemeColor.byte(red) << 16 | ThemeColor.byte(green) << 8 | ThemeColor.byte(blue)
    }

    var color: Color {
        Color(.sRGB, red: red, green: green, blue: blue)
    }

    var lightness: Double {
        luminance > ThemeColor.cieEpsilon ? 116 * cbrt(luminance) - 16 : ThemeColor.cieKappa * luminance
    }

    func contrast(with other: ThemeColor) -> Double {
        (max(luminance, other.luminance) + 0.05) / (min(luminance, other.luminance) + 0.05)
    }

    func mixed(with other: ThemeColor, amount: Double) -> ThemeColor {
        ThemeColor(
            channels: ThemeColor.quantized(red + (other.red - red) * amount),
            ThemeColor.quantized(green + (other.green - green) * amount),
            ThemeColor.quantized(blue + (other.blue - blue) * amount)
        )
    }

    func shiftingLightness(by delta: Double, toward target: ThemeColor) -> ThemeColor {
        let origin = lightness
        return firstMix(toward: target) { abs($0.lightness - origin) >= delta } ?? target
    }

    func ensuringContrast(_ minimum: Double, against layers: [ThemeColor], toward targets: [ThemeColor]) -> ThemeColor {
        let passes: (ThemeColor) -> Bool = { candidate in
            layers.allSatisfy { candidate.contrast(with: $0) >= minimum }
        }
        guard !passes(self) else { return self }
        return targets.lazy.compactMap { firstMix(toward: $0, where: passes) }.first ?? targets.last ?? self
    }

    private func firstMix(toward target: ThemeColor, where passes: (ThemeColor) -> Bool) -> ThemeColor? {
        (0 ... ThemeColor.searchSteps).lazy
            .map { mixed(with: target, amount: Double($0) / Double(ThemeColor.searchSteps)) }
            .first(where: passes)
    }

    private static let searchSteps = 200
    private static let cieEpsilon: Double = 216.0 / 24389
    private static let cieKappa: Double = 24389.0 / 27

    private static func quantized(_ value: Double) -> Double {
        (min(1, max(0, value)) * 255).rounded() / 255
    }

    private static func byte(_ value: Double) -> UInt32 {
        UInt32((value * 255).rounded())
    }

    private static func linearized(_ value: Double) -> Double {
        value <= 0.04045 ? value / 12.92 : pow((value + 0.055) / 1.055, 2.4)
    }
}
