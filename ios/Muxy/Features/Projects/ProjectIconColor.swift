import SwiftUI

nonisolated enum ProjectIconColor {
    static func color(for token: String?, fallback: Color) -> Color {
        guard let token, !token.isEmpty else { return fallback }
        if token.hasPrefix("#") { return hexColor(token) ?? fallback }
        return palette[token.lowercased()] ?? fallback
    }

    private static let palette: [String: Color] = [
        "red": .red,
        "orange": .orange,
        "yellow": .yellow,
        "green": .green,
        "mint": .mint,
        "teal": .teal,
        "cyan": .cyan,
        "blue": .blue,
        "indigo": .indigo,
        "violet": .purple,
        "purple": .purple,
        "pink": .pink,
        "brown": .brown,
        "gray": .gray,
        "grey": .gray
    ]

    private static func hexColor(_ token: String) -> Color? {
        var hex = token
        hex.removeFirst()

        if hex.count == 3 {
            hex = hex.map { "\($0)\($0)" }.joined()
        }

        guard hex.count == 6, let value = UInt32(hex, radix: 16) else { return nil }

        let red = Double((value >> 16) & 0xFF) / 255
        let green = Double((value >> 8) & 0xFF) / 255
        let blue = Double(value & 0xFF) / 255
        return Color(.sRGB, red: red, green: green, blue: blue)
    }
}
