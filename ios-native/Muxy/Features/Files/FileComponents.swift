import SwiftUI

struct FileBreadcrumbs: View {
    let path: String
    let rootName: String
    let onSelect: (String) -> Void

    @Environment(\.appTheme) private var theme

    private var paths: [String] {
        let parts = path.components(separatedBy: "/").filter { !$0.isEmpty }
        return [""] + parts.indices.map { parts.prefix($0 + 1).joined(separator: "/") }
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 2) {
                    ForEach(paths, id: \.self) { crumb in
                        if !crumb.isEmpty {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 9, weight: .semibold))
                                .foregroundStyle(theme.secondaryForeground)
                        }
                        Button { onSelect(crumb) } label: {
                            Text(crumb.isEmpty ? rootName : RemoteFilePath.name(crumb))
                                .font(.caption.weight(crumb == path ? .semibold : .regular))
                                .lineLimit(1)
                                .padding(.horizontal, 6)
                                .frame(minHeight: 44)
                                .foregroundStyle(crumb == path ? theme.foreground : theme.secondaryForeground)
                        }
                        .buttonStyle(.plain)
                        .id(crumb)
                        .accessibilityLabel("Open \(crumb.isEmpty ? rootName : crumb)")
                        .accessibilityValue(crumb == path ? "Current folder" : "")
                    }
                }
            }
            .onChange(of: path, initial: true) { _, path in
                proxy.scrollTo(path, anchor: .trailing)
            }
        }
    }
}

struct FileProjectHeader: View {
    let project: Project

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: project.icon ?? "folder")
                .font(.title3)
                .foregroundStyle(theme.accent)
                .frame(width: 38, height: 38)
                .filePanel()
            VStack(alignment: .leading, spacing: 4) {
                Text(project.name)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(theme.foreground)
                Label(project.workspaceKind == "ssh" ? "Remote host · Active worktree" : "Mac · Active worktree",
                      systemImage: project.workspaceKind == "ssh" ? "server.rack" : "desktopcomputer")
                    .font(.caption)
                    .foregroundStyle(theme.secondaryForeground)
                Text(project.path)
                    .font(.caption2.monospaced())
                    .foregroundStyle(theme.secondaryForeground)
                    .textSelection(.enabled)
            }
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
    }
}

struct FileIcon: View {
    let entry: RemoteFileEntry
    var prominent = false

    @Environment(\.appTheme) private var theme

    var body: some View {
        let presentation = FilePresentation(entry: entry)
        Image(systemName: presentation.symbol)
            .font(.system(size: 21, weight: .regular))
            .foregroundStyle(presentation.color(in: theme))
            .frame(width: prominent ? 38 : 32, height: prominent ? 40 : 32)
            .background(prominent ? theme.accent.opacity(0.08) : .clear, in: RoundedRectangle(cornerRadius: 10))
            .overlay {
                if prominent {
                    RoundedRectangle(cornerRadius: 10).strokeBorder(theme.separator, lineWidth: 1)
                }
            }
            .accessibilityHidden(true)
    }
}

struct FileEntryLabel: View {
    let entry: RemoteFileEntry
    var selectionMode = false
    var selected = false

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 11) {
            if selectionMode {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 21))
                    .foregroundStyle(selected ? theme.accent : theme.secondaryForeground)
                    .frame(width: 22)
                    .accessibilityHidden(true)
            }
            FileIcon(entry: entry)
            VStack(alignment: .leading, spacing: 4) {
                Text(entry.name)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(entry.isIgnored ? theme.secondaryForeground : theme.foreground)
                    .fixedSize(horizontal: false, vertical: true)
                if entry.isIgnored {
                    Text("Ignored by Git")
                        .font(.caption2)
                        .foregroundStyle(theme.secondaryForeground)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .overlay {
                            RoundedRectangle(cornerRadius: 4).strokeBorder(theme.separator, lineWidth: 1)
                        }
                } else {
                    Text(FilePresentation(entry: entry).typeName)
                        .font(.caption)
                        .foregroundStyle(theme.secondaryForeground)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if entry.isDirectory && !selectionMode {
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(theme.secondaryForeground)
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .frame(minHeight: 65)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct FileEntryGroup: View {
    let entries: [RemoteFileEntry]
    var selectionMode = false
    var selectedPaths: Set<String> = []
    var isEnabled = true
    var isEntryEnabled: (RemoteFileEntry) -> Bool = { _ in true }
    let onOpen: (RemoteFileEntry) -> Void
    var onSelect: ((RemoteFileEntry) -> Void)?

    @Environment(\.appTheme) private var theme

    var body: some View {
        LazyVStack(spacing: 0) {
            ForEach(entries) { entry in
                VStack(spacing: 0) {
                    if entry.id != entries.first?.id {
                        theme.separator.frame(height: 1)
                            .padding(.leading, selectionMode ? 88 : 55)
                    }
                    entryButton(entry)
                    .background(selectedPaths.contains(entry.path) ? theme.accent.opacity(0.1) : .clear)
                    .overlay(alignment: .leading) {
                        if selectedPaths.contains(entry.path) {
                            theme.accent.frame(width: 3)
                        }
                    }
                    .disabled(!isEnabled || !isEntryEnabled(entry))
                    .opacity(isEntryEnabled(entry) ? 1 : 0.45)
                }
            }
        }
        .filePanel()
    }

    @ViewBuilder
    private func entryButton(_ entry: RemoteFileEntry) -> some View {
        let button = Button { onOpen(entry) } label: {
            FileEntryLabel(entry: entry, selectionMode: selectionMode, selected: selectedPaths.contains(entry.path))
        }
        .buttonStyle(.plain)
        if let onSelect {
            button
                .onLongPressGesture {
                    guard isEnabled, isEntryEnabled(entry) else { return }
                    onSelect(entry)
                }
                .accessibilityAction(named: "Select") {
                    guard isEnabled, isEntryEnabled(entry) else { return }
                    onSelect(entry)
                }
        } else {
            button
        }
    }
}

struct FileSectionHeading: View {
    let title: String
    let count: Int

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack {
            Text(title.uppercased())
                .tracking(0.6)
            Spacer()
            Text(count.formatted())
                .monospacedDigit()
        }
        .font(.caption2.weight(.semibold))
        .foregroundStyle(theme.secondaryForeground)
        .padding(.horizontal, 2)
        .accessibilityAddTraits(.isHeader)
    }
}

struct FileGuidance: View {
    let message: String

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "info.circle")
                .padding(.top, 2)
            Text(message)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .font(.caption)
        .foregroundStyle(theme.secondaryForeground)
        .lineSpacing(3)
    }
}

struct FileNotice<Actions: View>: View {
    let title: String
    let message: String
    var systemImage = "exclamationmark.triangle"
    @ViewBuilder var actions: () -> Actions

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label {
                Text(title)
            } icon: {
                Image(systemName: systemImage)
                    .foregroundStyle(FileTint.warning.color(in: theme))
            }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(theme.foreground)
            Text(message)
                .font(.caption)
                .foregroundStyle(theme.secondaryForeground)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
            actions()
                .font(.caption.weight(.semibold))
                .tint(theme.accent)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(FileTint.warning.color(in: theme).opacity(0.07), in: RoundedRectangle(cornerRadius: 12))
        .overlay {
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(FileTint.warning.color(in: theme).opacity(0.45), lineWidth: 1)
        }
    }
}

enum FileNamePrompt {
    case file
    case folder
    case rename(RemoteFileEntry)

    var title: String {
        switch self {
        case .file: "New file"
        case .folder: "New folder"
        case .rename: "Rename item"
        }
    }

    var action: String {
        if case .rename = self { return "Rename" }
        return "Create"
    }

    var guidance: String {
        switch self {
        case .folder: "Choose a folder name without / or \\."
        case .file: "Include an extension, such as notes.md. Names cannot contain / or \\."
        case .rename: "Choose a name without / or \\. Keep the extension to preserve the file type."
        }
    }

    var initialName: String {
        if case let .rename(entry) = self { return entry.name }
        return ""
    }
}
