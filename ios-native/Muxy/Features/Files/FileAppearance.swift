import SwiftUI

enum FileTint {
    case folder
    case code
    case image
    case warning
    case destructive

    func color(in theme: AppTheme) -> Color {
        let index: Int
        switch self {
        case .folder, .warning: index = 3
        case .code: index = 6
        case .image: index = 2
        case .destructive: index = 1
        }
        guard let palette = theme.event.palette, palette.indices.contains(index) else { return theme.accent }
        let rgb = palette[index]
        let color = Color(
            .sRGB,
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255,
            opacity: 1
        )
        return color.mix(with: theme.foreground, by: theme.isDark ? 0.15 : 0.4)
    }
}

struct FilePresentation {
    let entry: RemoteFileEntry

    private var fileExtension: String { RemoteFilePath.fileExtension(entry.path) }

    private var isCode: Bool {
        ["swift", "kt", "kts", "js", "jsx", "ts", "tsx", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp", "m", "mm", "java", "sh", "zsh", "bash", "html", "css", "scss", "sql"].contains(fileExtension)
    }

    var symbol: String {
        if entry.isDirectory { return "folder" }
        if RemoteFilePath.isImage(entry.path) { return "photo" }
        if isCode { return "chevron.left.forwardslash.chevron.right" }
        if ["json", "yaml", "yml", "toml", "xml", "plist"].contains(fileExtension) { return "curlybraces" }
        return "doc.text"
    }

    var typeName: String {
        if entry.isDirectory { return "Folder" }
        if RemoteFilePath.isImage(entry.path) { return "Image" }
        switch fileExtension {
        case "swift": return "Swift source"
        case "md", "markdown": return "Markdown"
        case "json": return "JSON"
        case "yaml", "yml": return "YAML"
        case "ts", "tsx": return "TypeScript"
        case "js", "jsx": return "JavaScript"
        case "kt", "kts": return "Kotlin source"
        case "py": return "Python source"
        case "txt": return "Plain text"
        case "": return "File"
        default: return "\(fileExtension.uppercased()) file"
        }
    }

    func color(in theme: AppTheme) -> Color {
        if entry.isIgnored { return theme.secondaryForeground }
        if entry.isDirectory { return FileTint.folder.color(in: theme) }
        if RemoteFilePath.isImage(entry.path) { return FileTint.image.color(in: theme) }
        if isCode { return FileTint.code.color(in: theme) }
        return theme.accent
    }
}

extension View {
    func filePanel() -> some View {
        modifier(FilePanelModifier())
    }

    func fileFooter() -> some View {
        modifier(FileFooterModifier())
    }
}

private struct FilePanelModifier: ViewModifier {
    @Environment(\.appTheme) private var theme

    func body(content: Content) -> some View {
        content
            .background(theme.surface.opacity(0.6))
            .clipShape(RoundedRectangle(cornerRadius: 13))
            .overlay {
                RoundedRectangle(cornerRadius: 13)
                    .strokeBorder(theme.separator, lineWidth: 1)
                    .allowsHitTesting(false)
            }
    }
}

private struct FileFooterModifier: ViewModifier {
    @Environment(\.appTheme) private var theme

    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity)
            .background(theme.background)
            .overlay(alignment: .top) {
                theme.separator.frame(height: 1)
            }
    }
}

struct FilePrimaryButtonStyle: ButtonStyle {
    @Environment(\.appTheme) private var theme
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(theme.onAccent)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(minHeight: 44)
            .background(theme.accent, in: RoundedRectangle(cornerRadius: 11))
            .opacity(isEnabled ? configuration.isPressed ? 0.8 : 1 : 0.4)
    }
}
