import SwiftUI

struct WorktreesSheetView: View {
    @State var viewModel: WorktreesViewModel

    var body: some View {
        NavigationStack {
            GitWorktreesView(viewModel: viewModel)
        }
    }
}

struct GitWorktreesView: View {
    let viewModel: WorktreesViewModel
    @Environment(\.appTheme) private var theme
    @State private var selectedWorktreeID: UUID?
    @State private var removingWorktreeID: UUID?

    var body: some View {
        ThemedList {
            Section {
                NavigationLink {
                    GitNewWorktreeView(viewModel: viewModel)
                } label: {
                    Label("New Worktree", systemImage: "plus")
                        .foregroundStyle(theme.foreground)
                }
            }

            if let worktrees = viewModel.worktrees {
                Section {
                    ForEach(worktrees) { worktree in
                        Button {
                            select(worktree)
                        } label: {
                            HStack {
                                Text(worktree.name)
                                    .font(.headline)
                                    .foregroundStyle(theme.foreground)
                                Spacer()
                                if selectedWorktreeID == worktree.id || removingWorktreeID == worktree.id {
                                    ProgressView()
                                } else if worktree.id == viewModel.activeWorktreeID {
                                    Image(systemName: "checkmark")
                                        .font(.body.weight(.semibold))
                                        .foregroundStyle(theme.accent)
                                }
                            }
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .disabled(selectedWorktreeID != nil || removingWorktreeID != nil)
                        .buttonStyle(.plain)
                        .swipeActions {
                            if worktree.canBeRemoved {
                                Button(role: .destructive) {
                                    remove(worktree)
                                } label: {
                                    Label("Remove", systemImage: "trash")
                                }
                            }
                        }
                    }
                } header: {
                    ThemedSectionHeader("Worktrees")
                }
            } else if viewModel.isLoadingWorktrees {
                ProgressView()
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(theme.red)
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
                .tint(theme.foreground)
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

            Section {
                Button {
                    submit()
                } label: {
                    if isSubmitting {
                        ProgressView()
                    } else {
                        Text("Create Worktree")
                    }
                }
                .disabled(!canSubmit || isSubmitting)
            }

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
