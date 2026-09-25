import UIKit

nonisolated struct ThemePalette: Equatable, Sendable {
    let name: String
    let foreground: UInt32
    let background: UInt32
    let ansi: [UInt32]
    let cursor: UInt32
    let cursorText: UInt32
    let selectionBackground: UInt32
    let selectionForeground: UInt32
}

extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}
