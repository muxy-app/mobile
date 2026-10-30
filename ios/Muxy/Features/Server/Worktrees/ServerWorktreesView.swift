import SwiftUI

struct ServerWorktreesSheetView: View {
    @State var model: ServerWorktreesModel
    let onOpen: (String) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ServerWorktreesView(model: model, onOpen: onOpen)
                .toolbar {
                    DoneToolbarItem { dismiss() }
                }
        }
        .pageModalPresentation()
    }
}

struct ServerWorktreesView: View {
    let model: ServerWorktreesModel
    let onOpen: (String) -> Void

    var body: some View {
        CardList {
            CardSection {
                NavigationLink {
                    ServerNewWorktreeView(model: model)
                } label: {
                    CardRow("New Worktree", systemImage: "plus")
                }
                .buttonStyle(.cardRow)
            }

            if let rows = model.rows {
                CardSection("Worktrees", footer: removalHint(rows), separatorInset: CardMetrics.textInset) {
                    ForEach(rows) { row in
                        worktreeRow(row)
                    }
                }
            } else if model.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
            }

            if let errorMessage = model.errorMessage {
                CardSection {
                    CardMessageRow(errorMessage)
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
            CardRow(row.name, subtitle: subtitle(for: row)) {
                if model.busyRowID == row.id {
                    ProgressView()
                } else if row.isCurrent {
                    CardRowCheckmark()
                }
            }
        }
        .buttonStyle(.cardRow)
        .disabled(model.isBusy || !row.isOpenable)
        .accessibilityAddTraits(row.isCurrent ? .isSelected : [])
        .contextMenu {
            if row.isRemovable {
                Button("Remove", systemImage: "trash", role: .destructive) { prepareRemoval(of: row) }
            }
        }
        .accessibilityActions {
            if row.isRemovable {
                Button("Remove") { prepareRemoval(of: row) }
            }
        }
    }

    private func prepareRemoval(of row: ServerWorktreesModel.Row) {
        Task { await model.prepareRemoval(of: row) }
    }

    private func removalHint(_ rows: [ServerWorktreesModel.Row]) -> String? {
        rows.contains(where: \.isRemovable) ? "Touch and hold a worktree to remove it." : nil
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

            FormSubmitSection(
                "Create Worktree",
                isSubmitting: isSubmitting,
                isDisabled: branch.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                action: submit
            )

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
