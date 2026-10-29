import SwiftUI

struct FileBrowserView: View {
    @Bindable var viewModel: FileManagerViewModel
    let onNamePrompt: (FileNamePrompt) -> Void
    let onDelete: ([String]) -> Void

    @Environment(\.appTheme) private var theme
    @State private var filter = ""
    @FocusState private var isFilterFocused: Bool

    private var query: String { filter.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var filteredEntries: [RemoteFileEntry] {
        guard !query.isEmpty else { return viewModel.entries }
        return viewModel.entries.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }

    private var selectedEntry: RemoteFileEntry? {
        guard viewModel.selectedPaths.count == 1 else { return nil }
        return viewModel.entries.first { viewModel.selectedPaths.contains($0.path) }
    }

    private var guidance: String {
        if viewModel.selectionMode {
            return "Select the items you want to organize. Rename is available when one item is selected."
        }
        return viewModel.location.host.selectionGuidance
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                FileProjectHeader(location: viewModel.location)
                search
                FileBreadcrumbs(path: viewModel.currentPath, rootName: viewModel.location.name) { path in
                    isFilterFocused = false
                    Task { await viewModel.goToDirectory(path) }
                }
                .disabled(!viewModel.canMutate)
                .padding(.vertical, -8)
                directoryContent
                FileGuidance(message: guidance)
                    .padding(.horizontal, CardMetrics.rowPadding)
            }
            .padding(.horizontal, PageMargin.horizontal)
            .padding(.vertical, CardMetrics.rowPadding)
        }
        .background(theme.groupedBackground)
        .refreshable { await viewModel.refreshDirectory() }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom, spacing: 0) { footer }
        .onChange(of: viewModel.currentPath) { _, _ in filter = "" }
    }

    private var search: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(theme.secondaryForeground)
                .accessibilityHidden(true)
            TextField("Filter this folder", text: $filter)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .foregroundStyle(theme.foreground)
                .focused($isFilterFocused)
                .submitLabel(.done)
                .onSubmit { isFilterFocused = false }
            if !filter.isEmpty {
                Button { filter = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(theme.secondaryForeground)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Clear filter")
            }
        }
        .padding(.leading, CardMetrics.rowPadding)
        .padding(.trailing, filter.isEmpty ? CardMetrics.rowPadding : 0)
        .frame(minHeight: 44)
        .background(theme.secondaryGroupedBackground, in: Capsule())
        .overlay {
            if isFilterFocused {
                Capsule().strokeBorder(theme.accent, lineWidth: 1)
            }
        }
    }

    @ViewBuilder
    private var directoryContent: some View {
        if viewModel.isLoadingDirectory && viewModel.entries.isEmpty {
            ProgressView("Loading files…")
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
        } else if filteredEntries.isEmpty {
            ThemedEmptyState(
                title: query.isEmpty ? "This folder is empty" : "No matching files",
                systemImage: "folder",
                message: query.isEmpty ? "Use New to create a file or folder here." : "Try a different name or clear the filter."
            ) {
                if !query.isEmpty {
                    Button("Clear filter") { filter = "" }
                        .buttonStyle(ThemedBorderedButtonStyle())
                }
            }
        } else {
            entrySection("Folders", entries: filteredEntries.filter(\.isDirectory))
            entrySection("Files", entries: filteredEntries.filter { !$0.isDirectory })
        }
    }

    @ViewBuilder
    private func entrySection(_ title: String, entries: [RemoteFileEntry]) -> some View {
        if !entries.isEmpty {
            VStack(alignment: .leading, spacing: CardMetrics.headerSpacing) {
                CardSectionHeader(title, detail: entries.count.formatted())
                FileEntryGroup(
                    entries: entries,
                    selectionMode: viewModel.selectionMode,
                    selectedPaths: viewModel.selectedPaths,
                    isEnabled: viewModel.canMutate,
                    onOpen: { entry in
                        isFilterFocused = false
                        Task { await viewModel.open(entry) }
                    },
                    onSelect: { entry in
                        isFilterFocused = false
                        viewModel.toggleSelection(entry.path)
                    }
                )
            }
            .padding(.bottom, 4)
        }
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 10) {
            if viewModel.selectionMode {
                Text("\(viewModel.selectedPaths.count) \(viewModel.selectedPaths.count == 1 ? "item" : "items") selected")
                    .font(.caption)
                    .foregroundStyle(theme.secondaryForeground)
                    .accessibilityAddTraits(.updatesFrequently)
                HStack(spacing: 8) {
                    selectionAction("Move", symbol: "folder", disabled: viewModel.selectedPaths.isEmpty) {
                        Task { await viewModel.startMove(viewModel.selectedPaths.sorted()) }
                    }
                    selectionAction("Rename", symbol: "pencil", disabled: selectedEntry == nil) {
                        if let selectedEntry { onNamePrompt(.rename(selectedEntry)) }
                    }
                    selectionAction(
                        viewModel.location.host.deletionShortTitle,
                        symbol: "trash",
                        disabled: viewModel.selectedPaths.isEmpty,
                        destructive: true
                    ) {
                        onDelete(viewModel.selectedPaths.sorted())
                    }
                }
            } else {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(query.isEmpty
                            ? "\(viewModel.entries.count) \(viewModel.entries.count == 1 ? "item" : "items")"
                            : "\(filteredEntries.count) of \(viewModel.entries.count) items")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(theme.foreground)
                        Text(viewModel.currentPath.isEmpty ? "Project root" : RemoteFilePath.name(viewModel.currentPath))
                            .font(.caption)
                            .foregroundStyle(theme.secondaryForeground)
                            .lineLimit(2)
                    }
                    Spacer(minLength: 0)
                    Menu {
                        Button { onNamePrompt(.file) } label: { Label("New file", systemImage: "doc.badge.plus") }
                        Button { onNamePrompt(.folder) } label: { Label("New folder", systemImage: "folder.badge.plus") }
                    } label: {
                        Label("New", systemImage: "plus")
                    }
                    .buttonStyle(ThemedProminentButtonStyle())
                    .disabled(!viewModel.canMutate || viewModel.isLoadingDirectory)
                    .accessibilityLabel("Create file or folder")
                }
            }
        }
        .fileFooter()
    }

    private func selectionAction(
        _ title: String,
        symbol: String,
        disabled: Bool,
        destructive: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(role: destructive ? .destructive : nil, action: action) {
            VStack(spacing: 5) {
                Image(systemName: symbol)
                    .font(.body)
                Text(title)
                    .font(.caption)
            }
            .foregroundStyle(destructive ? FileTint.destructive.color(in: theme) : theme.foreground)
            .frame(maxWidth: .infinity, minHeight: 58)
            .cardSurface()
        }
        .buttonStyle(.cardRow)
        .disabled(disabled || !viewModel.canMutate)
    }
}
