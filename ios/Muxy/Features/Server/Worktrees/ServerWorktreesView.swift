import SwiftUI

struct ServerWorktreesSheetView: View {
    @State var model: ServerWorktreesModel
    let onOpen: (String) -> Void

    var body: some View {
        NavigationStack {
            ServerWorktreesView(model: model, onOpen: onOpen)
        }
    }
}

struct ServerWorktreesView: View {
    let model: ServerWorktreesModel
    let onOpen: (String) -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        ThemedList {
            Section {
                NavigationLink {
                    ServerNewWorktreeView(model: model)
                } label: {
                    Label("New Worktree", systemImage: "plus")
                        .foregroundStyle(theme.foreground)
                }
            }

            if let rows = model.rows {
                Section {
                    ForEach(rows) { row in
                        worktreeRow(row)
                    }
                } header: {
                    ThemedSectionHeader("Worktrees")
                }
            } else if model.isLoading {
                ProgressView()
            }

            if let errorMessage = model.errorMessage {
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
                    Task { await model.refresh() }
                } label: {
                    if model.isLoading {
                        ProgressView()
                    } else {
                        Image(systemName: "arrow.clockwise")
                    }
                }
                .disabled(model.isLoading)
                .tint(theme.foreground)
                .accessibilityLabel("Refresh Worktrees")
            }
        }
        .confirmationDialog(
            "Remove Worktree?",
            isPresented: removalBinding,
            titleVisibility: .visible,
            presenting: model.pendingRemoval
        ) { pending in
            Button("Remove", role: .destructive) {
                Task { await model.remove(pending) }
            }
            Button("Cancel", role: .cancel) {}
        } message: { pending in
            Text(removalMessage(for: pending))
        }
        .task {
            if model.worktrees == nil {
                await model.refresh()
            }
        }
        .refreshable { await model.refresh() }
    }

    private func worktreeRow(_ row: ServerWorktreesModel.Row) -> some View {
        Button {
            open(row)
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text(row.name)
                        .font(.headline)
                        .foregroundStyle(theme.foreground)
                    Text(subtitle(for: row))
                        .font(.caption)
                        .foregroundStyle(theme.secondaryForeground)
                }
                Spacer()
                if model.busyRowID == row.id {
                    ProgressView()
                } else if row.isCurrent {
                    Image(systemName: "checkmark")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(theme.accent)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .contentShape(Rectangle())
        }
        .disabled(model.isBusy || !row.isOpenable)
        .buttonStyle(.plain)
        .swipeActions {
            if row.isRemovable {
                Button(role: .destructive) {
                    Task { await model.prepareRemoval(of: row) }
                } label: {
                    Label("Remove", systemImage: "trash")
                }
            }
        }
    }

    private var removalBinding: Binding<Bool> {
        Binding(
            get: { model.pendingRemoval != nil },
            set: { isPresented in
                guard !isPresented else { return }
                model.cancelRemoval()
            }
        )
    }

    private func subtitle(for row: ServerWorktreesModel.Row) -> String {
        let branch = row.branch ?? "Detached HEAD"
        guard row.projectID == nil else { return branch }
        return "\(branch) · Not in Muxy"
    }

    private func removalMessage(for pending: ServerWorktreesModel.PendingRemoval) -> String {
        let consequence = "“\(pending.row.name)” and its folder will be deleted, and its terminals will end."
        guard pending.hasUncommittedChanges else { return consequence }
        return "\(consequence) It has uncommitted changes that will be lost."
    }

    private func open(_ row: ServerWorktreesModel.Row) {
        guard !row.isCurrent else { return }
        Task {
            guard let projectID = await model.projectToOpen(row) else { return }
            onOpen(projectID)
        }
    }
}

struct ServerNewWorktreeView: View {
    let model: ServerWorktreesModel

    @State private var branch = ""
    @State private var createsBranch = true
    @State private var isSubmitting = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme

    var body: some View {
        ThemedForm {
            Section {
                TextField("Branch", text: $branch)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Toggle("Create branch", isOn: $createsBranch)
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
                .disabled(branch.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSubmitting)
            }

            if let errorMessage = model.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(theme.red)
                }
            }
        }
        .screenTitle("New Worktree")
    }

    private func submit() {
        isSubmitting = true
        Task {
            let didCreate = await model.create(branch: branch, createsBranch: createsBranch)
            isSubmitting = false
            if didCreate {
                dismiss()
            }
        }
    }
}
