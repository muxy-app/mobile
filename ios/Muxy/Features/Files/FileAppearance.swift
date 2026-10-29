import SwiftUI

enum FileTint {
    case success
    case warning
    case destructive

    func color(in theme: AppTheme) -> Color {
        switch self {
        case .success: theme.green
        case .warning: theme.yellow
        case .destructive: theme.red
        }
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
}

extension View {
    func fileFooter() -> some View {
        modifier(FileFooterModifier())
    }
}

private struct FileFooterModifier: ViewModifier {
    @Environment(\.appTheme) private var theme

    func body(content: Content) -> some View {
        content
            .padding(.horizontal, PageMargin.horizontal)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity)
            .background(theme.groupedBackground)
            .overlay(alignment: .top) {
                theme.separator.frame(height: CardMetrics.separatorHeight)
            }
    }
}
