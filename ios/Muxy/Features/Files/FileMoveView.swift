import SwiftUI

struct FileMoveView: View {
    let viewModel: FileManagerViewModel

    @Environment(\.appTheme) private var theme

    private var itemNames: String {
        viewModel.movingPaths.map(RemoteFilePath.name).joined(separator: ", ")
    }

    private var destination: String {
        guard !viewModel.movePath.isEmpty else { return viewModel.location.name }
        return "\(viewModel.location.name) / \(viewModel.movePath.replacingOccurrences(of: "/", with: " / "))"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "folder.badge.arrowshape.forward")
                        .font(.title3)
                        .foregroundStyle(theme.secondaryForeground)
                    VStack(alignment: .leading, spacing: 5) {
                        Text("Moving \(viewModel.movingPaths.count) \(viewModel.movingPaths.count == 1 ? "item" : "items")")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(theme.foreground)
                        Text(itemNames)
                            .font(.caption)
                            .foregroundStyle(theme.secondaryForeground)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(CardMetrics.rowPadding)
                .cardSurface()

                FileGuidance(message: "Open a folder, then tap Move here. A folder can’t be moved inside itself. Existing items are kept; moved items may be given a new name to avoid a conflict.")
                    .padding(.horizontal, CardMetrics.rowPadding)

                FileBreadcrumbs(path: viewModel.movePath, rootName: viewModel.location.name) { path in
                    Task { await viewModel.goToMoveDirectory(path) }
                }
                .disabled(!viewModel.canMutate)
                .padding(.vertical, -8)

                VStack(alignment: .leading, spacing: CardMetrics.headerSpacing) {
                    CardSectionHeader("Destination folders", detail: viewModel.moveEntries.count.formatted())
                    if viewModel.isLoadingMove {
                        ProgressView("Loading folders…")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 40)
                    } else if viewModel.moveEntries.isEmpty {
                        ThemedEmptyState(
                            title: "No subfolders here",
                            systemImage: "folder",
                            message: "You can still move your items into this folder."
                        )
                    } else {
                        FileEntryGroup(
                            entries: viewModel.moveEntries,
                            isEnabled: viewModel.canMutate,
                            isEntryEnabled: { entry in
                                !viewModel.movingPaths.contains { RemoteFilePath.contains(entry.path, in: $0) }
                            },
                            onOpen: { entry in
                                Task { await viewModel.goToMoveDirectory(entry.path) }
                            }
                        )
                    }
                }
            }
            .padding(.horizontal, PageMargin.horizontal)
            .padding(.vertical, CardMetrics.rowPadding)
        }
        .background(theme.groupedBackground)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(alignment: .leading, spacing: 10) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Destination")
                        .font(.caption)
                        .foregroundStyle(theme.secondaryForeground)
                    Text(destination)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(theme.foreground)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Button { Task { await viewModel.moveHere() } } label: {
                    Label("Move here", systemImage: "folder.badge.arrowshape.forward")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(ThemedProminentButtonStyle())
                .disabled(!viewModel.canMoveHere)
            }
            .fileFooter()
        }
    }
}
