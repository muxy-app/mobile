import Foundation
import SwiftUI

struct DeviceTheme: Equatable {
    let fg: UInt32
    let bg: UInt32
    let palette: [UInt32]

    var fgColor: Color { Self.color(rgb: fg) }
    var bgColor: Color { Self.color(rgb: bg) }

    var isDark: Bool {
        let r = Double((bg >> 16) & 0xFF) / 255.0
        let g = Double((bg >> 8) & 0xFF) / 255.0
        let b = Double(bg & 0xFF) / 255.0
        let luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance < 0.5
    }

    private static func color(rgb: UInt32) -> Color {
        Color(
            red: Double((rgb >> 16) & 0xFF) / 255.0,
            green: Double((rgb >> 8) & 0xFF) / 255.0,
            blue: Double(rgb & 0xFF) / 255.0
        )
    }
}
