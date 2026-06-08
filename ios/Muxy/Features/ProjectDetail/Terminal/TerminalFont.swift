import UIKit

nonisolated enum TerminalFont {
    static let defaultSize: CGFloat = 13
    static let minimumSize: CGFloat = 9
    static let maximumSize: CGFloat = 28

    struct Faces {
        let normal: UIFont
        let bold: UIFont
        let italic: UIFont
        let boldItalic: UIFont
    }

    private enum PostScriptName {
        static let normal = "CaskaydiaMonoNFM-Regular"
        static let bold = "CaskaydiaMonoNFM-Bold"
        static let italic = "CaskaydiaMonoNFM-Italic"
        static let boldItalic = "CaskaydiaMonoNFM-BoldItalic"
    }

    static func faces(size: CGFloat) -> Faces {
        let normal = font(PostScriptName.normal, size: size)
            ?? UIFont.monospacedSystemFont(ofSize: size, weight: .regular)
        let bold = font(PostScriptName.bold, size: size)
            ?? UIFont.monospacedSystemFont(ofSize: size, weight: .bold)
        let italic = font(PostScriptName.italic, size: size) ?? normal
        let boldItalic = font(PostScriptName.boldItalic, size: size) ?? bold
        return Faces(normal: normal, bold: bold, italic: italic, boldItalic: boldItalic)
    }

    static func mono(size: CGFloat) -> UIFont {
        faces(size: size).normal
    }

    private static func font(_ name: String, size: CGFloat) -> UIFont? {
        UIFont(name: name, size: size)
    }
}
