import SwiftUI

struct FileSheetView: View {
    @State var viewModel: FileManagerViewModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme
    @State private var namePrompt: FileNamePrompt?
    @State private var enteredName = ""
    @State private var isNamePromptPresented = false
    @State private var pendingDelete: [String] = []
    @State private var pendingNavigation = NavigationIntent.back
    @State private var isConfirmationPresented = false
    @State private var promptContextID: UUID?

    private enum NavigationIntent {
        case close
        case back
        case reload
    }

    private var title: String {
        switch viewModel.route {
        case .browser: "Files"
        case .preview: viewModel.preview?.isEditing == true ? "Edit file" : "Preview"
        case .move: "Move items"
        }
    }

    private var deletionTitle: String {
        viewModel.project.workspaceKind == "ssh" ? "Delete permanently?" : "Move to Trash?"
    }

    private var refreshTitle: String {
        switch viewModel.route {
        case .browser: "Refresh files"
        case .preview: "Reload file"
        case .move: "Reload folders"
        }
    }

    private var nameError: String? {
        do {
            _ = try RemoteFilePath.validatedName(enteredName)
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                status
                screen
            }
            .background(theme.background)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbar }
        }
        .tint(theme.accent)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(viewModel.isDirty || viewModel.isBusy)
        .task { await viewModel.run() }
        .onChange(of: viewModel.contextID) { _, _ in
            isNamePromptPresented = false
            isConfirmationPresented = false
            pendingDelete = []
        }
        .alert(namePrompt?.title ?? "Name", isPresented: $isNamePromptPresented, presenting: namePrompt) { prompt in
            TextField("Name", text: $enteredName)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button("Cancel", role: .cancel) {}
            Button(prompt.action) { submit(prompt) }
                .disabled(nameError != nil || !viewModel.canMutate)
        } message: { prompt in
            Text(!enteredName.isEmpty ? nameError ?? prompt.guidance : prompt.guidance)
        }
        .confirmationDialog(
            pendingDelete.isEmpty ? "Unsaved changes" : deletionTitle,
            isPresented: $isConfirmationPresented,
            titleVisibility: .visible
        ) {
            confirmationActions
        } message: {
            confirmationMessage
        }
    }

    @ViewBuilder
    private var screen: some View {
        switch viewModel.route {
        case .browser:
            FileBrowserView(viewModel: viewModel, onNamePrompt: presentNamePrompt, onDelete: confirmDelete)
        case .preview:
            if let preview = viewModel.preview {
                FilePreviewView(
                    viewModel: viewModel,
                    preview: preview,
                    onReload: { requestNavigation(.reload) }
                )
                .id(preview.entry.path)
            }
        case .move:
            FileMoveView(viewModel: viewModel)
        }
    }

    @ViewBuilder
    private var status: some View {
        if !viewModel.isConnected {
            FileNotice(title: "Connection lost", message: "Reconnect to your Mac to browse files. Any unsaved edits are still here.", systemImage: "wifi.slash") {}
                .padding(.horizontal, 18)
                .padding(.top, 12)
        }
        if viewModel.hasContextChanged {
            FileNotice(
                title: "Worktree changed",
                message: "Your draft is still here. Copy anything you need before returning to Files. It can’t be saved in the new worktree.",
                systemImage: "arrow.triangle.branch"
            ) {
                if viewModel.route == .browser {
                    Button("Reload Files") { Task { await viewModel.returnToBrowser() } }
                        .frame(minHeight: 44)
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 12)
        } else if let error = viewModel.errorMessage {
            FileNotice(title: "Couldn’t complete the action", message: error) {
                Button(refreshTitle) { Task { await refresh() } }
                    .frame(minHeight: 44)
                    .disabled(!viewModel.canMutate)
            }
            .padding(.horizontal, 18)
            .padding(.top, 12)
        }
        if viewModel.isBusy {
            ProgressView()
                .padding(8)
                .accessibilityLabel("Updating files")
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            if viewModel.route == .browser {
                Button(viewModel.selectionMode ? "Done" : "Select") {
                    if viewModel.selectionMode {
                        viewModel.clearSelection()
                    } else {
                        viewModel.selectionMode = true
                    }
                }
                .disabled(!viewModel.canMutate || viewModel.entries.isEmpty)
            } else {
                Button { requestNavigation(.back) } label: {
                    Label("Files", systemImage: "chevron.left")
                }
                .disabled(viewModel.isBusy)
            }
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            if viewModel.route == .preview, let preview = viewModel.preview {
                Menu {
                    Group {
                        Button { presentNamePrompt(.rename(preview.entry)) } label: { Label("Rename", systemImage: "pencil") }
                        Button { Task { await viewModel.startMove([preview.entry.path]) } } label: { Label("Move", systemImage: "folder") }
                        Button(role: .destructive) { confirmDelete([preview.entry.path]) } label: {
                            Label(viewModel.project.workspaceKind == "ssh" ? "Delete permanently" : "Move to Trash", systemImage: "trash")
                        }
                    }
                    .disabled(!viewModel.canMutate || preview.isEditing || viewModel.isLoadingPreview)
                    Divider()
                    Button { requestNavigation(.close) } label: { Label("Close Files", systemImage: "xmark") }
                        .disabled(viewModel.isBusy)
                } label: {
                    Image(systemName: "ellipsis")
                }
                .accessibilityLabel("File actions")
            } else {
                Button { requestNavigation(.close) } label: {
                    Image(systemName: "xmark")
                }
                .accessibilityLabel("Close Files")
                .disabled(viewModel.isBusy)
            }
        }
    }

    @ViewBuilder
    private var confirmationActions: some View {
        if !pendingDelete.isEmpty {
            Button(viewModel.project.workspaceKind == "ssh" ? "Delete" : "Move to Trash", role: .destructive) {
                guard promptContextID == viewModel.contextID else { return }
                let paths = pendingDelete
                Task { await viewModel.delete(paths) }
            }
            Button("Cancel", role: .cancel) {}
        } else {
            if viewModel.canMutate, pendingNavigation != .reload {
                Button("Save") {
                    Task {
                        guard await viewModel.save() else { return }
                        await navigate()
                    }
                }
            }
            Button(pendingNavigation == .reload ? "Discard and reload" : "Discard", role: .destructive) {
                Task { await navigate() }
            }
            Button("Keep editing", role: .cancel) {}
        }
    }

    @ViewBuilder
    private var confirmationMessage: some View {
        if !pendingDelete.isEmpty {
            let items = pendingDelete.count == 1 ? "“\(RemoteFilePath.name(pendingDelete[0]))”" : "\(pendingDelete.count) selected items"
            Text(viewModel.project.workspaceKind == "ssh"
                ? "\(items) will be permanently deleted from the remote host."
                : "\(items) will be moved to Trash on the Mac.")
        } else if viewModel.hasContextChanged {
            Text("Copy anything you need from this draft before discarding it.")
        } else {
            Text("Your file has unsaved edits.")
        }
    }

    private func presentNamePrompt(_ prompt: FileNamePrompt) {
        promptContextID = viewModel.contextID
        namePrompt = prompt
        enteredName = prompt.initialName
        isNamePromptPresented = true
    }

    private func submit(_ prompt: FileNamePrompt) {
        guard promptContextID == viewModel.contextID else { return }
        let name = enteredName
        Task {
            switch prompt {
            case .file: _ = await viewModel.create(name: name, isDirectory: false)
            case .folder: _ = await viewModel.create(name: name, isDirectory: true)
            case let .rename(entry): _ = await viewModel.rename(entry, name: name)
            }
        }
    }

    private func confirmDelete(_ paths: [String]) {
        guard !paths.isEmpty else { return }
        promptContextID = viewModel.contextID
        pendingDelete = paths
        isConfirmationPresented = true
    }

    private func requestNavigation(_ intent: NavigationIntent) {
        pendingDelete = []
        pendingNavigation = intent
        guard viewModel.isDirty else {
            Task { await navigate() }
            return
        }
        isConfirmationPresented = true
    }

    private func navigate() async {
        switch pendingNavigation {
        case .close: dismiss()
        case .back: await viewModel.returnToBrowser()
        case .reload: await viewModel.reloadPreview()
        }
    }

    private func refresh() async {
        switch viewModel.route {
        case .browser: await viewModel.refreshDirectory()
        case .preview: requestNavigation(.reload)
        case .move: await viewModel.goToMoveDirectory(viewModel.movePath)
        }
    }
}
