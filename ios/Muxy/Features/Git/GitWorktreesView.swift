import SwiftUI

struct WorktreesSheetView: View {
    @State var viewModel: WorktreesViewModel

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            GitWorktreesView(viewModel: viewModel)
                .toolbar {
                    DoneToolbarItem { dismiss() }
                }
        }
        .presentationDetents([.large])
    }
}

struct GitWorktreesView: View {
    let viewModel: WorktreesViewModel
    @State private var selectedWorktreeID: UUID?
    @State private var removingWorktreeID: UUID?

    var body: some View {
        CardList {
            CardSection {
                NavigationLink {
                    GitNewWorktreeView(viewModel: viewModel)
                } label: {
                    CardRow("New Worktree", systemImage: "plus")
                }
                .buttonStyle(.cardRow)
            }

            if let worktrees = viewModel.worktrees {
                CardSection("Worktrees", footer: removalHint(worktrees), separatorInset: CardMetrics.textInset) {
                    ForEach(worktrees) { worktree in
                        worktreeRow(worktree)
                    }
                }
            } else if viewModel.isLoadingWorktrees {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
            }

            if let errorMessage = viewModel.errorMessage {
                CardSection {
                    CardMessageRow(errorMessage)
                }
            }
        }
        .screenTitle("Worktrees")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    Task { await viewModel.refreshWorktrees() }
                } label: {
                    if viewModel.isLoadingWorktrees {
                        ProgressView()
                    } else {
                        Image(systemName: "arrow.clockwise")
                    }
                }
                .disabled(viewModel.isLoadingWorktrees)
                .accessibilityLabel("Sync Worktrees with Desktop")
            }
        }
        .task {
            if viewModel.worktrees == nil {
                await viewModel.refreshWorktrees()
            }
        }
        .refreshable { await viewModel.refreshWorktrees() }
    }

    private func worktreeRow(_ worktree: Worktree) -> some View {
        Button {
            select(worktree)
        } label: {
            CardRow(worktree.name) {
                if selectedWorktreeID == worktree.id || removingWorktreeID == worktree.id {
                    ProgressView()
                } else if worktree.id == viewModel.activeWorktreeID {
                    CardRowCheckmark()
                }
            }
        }
        .buttonStyle(.cardRow)
        .disabled(selectedWorktreeID != nil || removingWorktreeID != nil)
        .accessibilityAddTraits(worktree.id == viewModel.activeWorktreeID ? .isSelected : [])
        .contextMenu {
            if worktree.canBeRemoved {
                Button("Remove", systemImage: "trash", role: .destructive) { remove(worktree) }
            }
        }
        .accessibilityActions {
            if worktree.canBeRemoved {
                Button("Remove") { remove(worktree) }
            }
        }
    }

    private func removalHint(_ worktrees: [Worktree]) -> String? {
        worktrees.contains(where: \.canBeRemoved) ? "Touch and hold a worktree to remove it." : nil
    }

    private func select(_ worktree: Worktree) {
        selectedWorktreeID = worktree.id
        Task {
            await viewModel.selectWorktree(worktree)
            selectedWorktreeID = nil
        }
    }

    private func remove(_ worktree: Worktree) {
        removingWorktreeID = worktree.id
        Task {
            await viewModel.removeWorktree(worktree)
            removingWorktreeID = nil
        }
    }
}

struct GitNewWorktreeView: View {
    let viewModel: WorktreesViewModel
    @State private var name = ""
    @State private var branch = ""
    @State private var createBranch = true
    @State private var isSubmitting = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme

    var body: some View {
        ThemedForm {
            Section {
                TextField("Name", text: $name)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Branch", text: $branch)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Toggle("Create branch", isOn: $createBranch)
            } header: {
                ThemedSectionHeader("Worktree")
            }
            .foregroundStyle(theme.foreground)

            FormSubmitSection("Create Worktree", isSubmitting: isSubmitting, isDisabled: !canSubmit, action: submit)

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(theme.red)
                }
            }
        }
        .screenTitle("New Worktree")
    }

    private var canSubmit: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !branch.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func submit() {
        isSubmitting = true
        Task {
            let didCreate = await viewModel.addWorktree(name: name, branch: branch, createBranch: createBranch)
            isSubmitting = false
            if didCreate {
                dismiss()
            }
        }
    }
}
