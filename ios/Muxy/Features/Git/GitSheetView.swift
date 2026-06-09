import SwiftUI

struct GitSheetView: View {
    @State var viewModel: GitViewModel

    var body: some View {
        NavigationStack {
            GitOverviewView(viewModel: viewModel)
                .navigationTitle("Git")
                .navigationBarTitleDisplayMode(.inline)
        }
        .task {
            if viewModel.status == nil {
                await viewModel.refreshStatus()
            }
        }
    }
}

struct GitOverviewView: View {
    let viewModel: GitViewModel

    var body: some View {
        List {
            if let status = viewModel.status {
                branchSection(status)
                actionSection(status)
                manageSection(status)
                changesSection(status)
            } else if viewModel.isLoadingStatus {
                ProgressView()
            } else {
                ContentUnavailableView {
                    Label("No Git Information", systemImage: "arrow.triangle.branch")
                } description: {
                    Text("Git status is not available for this project.")
                }
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                }
            }
        }
        .refreshable { await viewModel.refreshStatus() }
    }

    private func branchSection(_ status: VCSStatus) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                Label(status.branch, systemImage: "arrow.triangle.branch")
                    .font(.headline)
                if status.hasUpstream {
                    HStack(spacing: 16) {
                        Label("\(status.behindCount)", systemImage: "arrow.down")
                        Label("\(status.aheadCount)", systemImage: "arrow.up")
                    }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                } else {
                    Text("No upstream")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.vertical, 4)
        }
    }

    private func actionSection(_ status: VCSStatus) -> some View {
        Section {
            Button {
                Task { await viewModel.pull() }
            } label: {
                Label("Pull", systemImage: "arrow.down")
            }
            .disabled(!status.hasUpstream)

            Button {
                Task { await viewModel.push() }
            } label: {
                Label(status.aheadCount > 0 ? "Push \(status.aheadCount)" : "Push", systemImage: "arrow.up")
            }
            .disabled(status.aheadCount == 0)

            NavigationLink {
                GitCommitView(viewModel: viewModel)
            } label: {
                Label(viewModel.totalChanges > 0 ? "Commit \(viewModel.totalChanges)" : "Commit", systemImage: "checkmark.circle")
            }
            .disabled(viewModel.totalChanges == 0)
        }
    }

    private func manageSection(_ status: VCSStatus) -> some View {
        Section("Manage") {
            NavigationLink {
                GitBranchesView(viewModel: viewModel)
            } label: {
                Label("Branches", systemImage: "arrow.triangle.branch")
            }

            NavigationLink {
                GitWorktreesView(viewModel: viewModel)
            } label: {
                Label("Worktrees", systemImage: "folder")
            }

            if let pullRequest = status.pullRequest, let url = URL(string: pullRequest.url) {
                NavigationLink {
                    GitPullRequestView(viewModel: viewModel, pullRequest: pullRequest)
                } label: {
                    Label("Pull Request #\(pullRequest.number)", systemImage: "arrow.triangle.pull")
                }
                Link(destination: url) {
                    Label("Open Pull Request", systemImage: "safari")
                }
            } else {
                NavigationLink {
                    GitCreatePullRequestView(viewModel: viewModel)
                } label: {
                    Label("New Pull Request", systemImage: "arrow.triangle.pull")
                }
            }
        }
    }

    private func changesSection(_ status: VCSStatus) -> some View {
        Section(viewModel.totalChanges > 0 ? "Changes" : "Status") {
            if viewModel.totalChanges == 0 {
                Label("Working tree clean", systemImage: "checkmark.circle")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(status.stagedFiles) { file in
                    NavigationLink {
                        GitDiffView(viewModel: viewModel, filePath: file.path)
                    } label: {
                        GitFileRow(file: file, isStaged: true)
                    }
                }
                ForEach(status.changedFiles) { file in
                    NavigationLink {
                        GitDiffView(viewModel: viewModel, filePath: file.path)
                    } label: {
                        GitFileRow(file: file, isStaged: false)
                    }
                }
            }
        }
    }
}

struct GitCommitView: View {
    let viewModel: GitViewModel
    @State private var message = ""
    @State private var stageAll = true
    @State private var isSubmitting = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section("Commit Message") {
                TextField("Describe your change", text: $message, axis: .vertical)
                    .lineLimit(3...8)
            }

            Section {
                Toggle("Stage all changes", isOn: $stageAll)
                Button {
                    submit()
                } label: {
                    if isSubmitting {
                        ProgressView()
                    } else {
                        Text("Commit")
                    }
                }
                .disabled(!canCommit || isSubmitting)
            }

            if let status = viewModel.status {
                Section("Changes") {
                    ForEach(status.stagedFiles) { file in
                        GitFileRow(file: file, isStaged: true)
                    }
                    ForEach(status.changedFiles) { file in
                        GitFileRow(file: file, isStaged: false)
                    }
                }
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Commit")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var canCommit: Bool {
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        guard let status = viewModel.status else { return false }
        if stageAll {
            return !status.stagedFiles.isEmpty || !status.changedFiles.isEmpty
        }
        return !status.stagedFiles.isEmpty
    }

    private func submit() {
        isSubmitting = true
        Task {
            let didCommit = await viewModel.commit(message: message, stageAll: stageAll)
            isSubmitting = false
            if didCommit {
                dismiss()
            }
        }
    }
}

struct GitBranchesView: View {
    let viewModel: GitViewModel
    @State private var switchingBranch: String?

    var body: some View {
        List {
            Section {
                NavigationLink {
                    GitNewBranchView(viewModel: viewModel)
                } label: {
                    Label("New Branch", systemImage: "plus")
                }
            }

            if let branches = viewModel.branches {
                Section("Local") {
                    ForEach(branches.locals, id: \.self) { branch in
                        Button {
                            switchBranch(branch)
                        } label: {
                            HStack {
                                Label(branch, systemImage: branch == branches.current ? "checkmark" : "arrow.triangle.branch")
                                Spacer()
                                if switchingBranch == branch {
                                    ProgressView()
                                } else if branch == branches.defaultBranch {
                                    Text("default")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .disabled(branch == branches.current || switchingBranch != nil)
                    }
                }
            } else if viewModel.isLoadingBranches {
                ProgressView()
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Branches")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if viewModel.branches == nil {
                await viewModel.refreshBranches()
            }
        }
        .refreshable { await viewModel.refreshBranches() }
    }

    private func switchBranch(_ branch: String) {
        switchingBranch = branch
        Task {
            await viewModel.switchBranch(branch)
            switchingBranch = nil
        }
    }
}

struct GitNewBranchView: View {
    let viewModel: GitViewModel
    @State private var name = ""
    @State private var isSubmitting = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Form {
            Section("Branch Name") {
                TextField("feature/name", text: $name)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }

            Section {
                Button {
                    submit()
                } label: {
                    if isSubmitting {
                        ProgressView()
                    } else {
                        Text("Create Branch")
                    }
                }
                .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSubmitting)
            }
        }
        .navigationTitle("New Branch")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func submit() {
        isSubmitting = true
        Task {
            let didCreate = await viewModel.createBranch(name)
            isSubmitting = false
            if didCreate {
                dismiss()
            }
        }
    }
}

struct GitWorktreesView: View {
    let viewModel: GitViewModel
    @State private var selectedWorktreeID: UUID?
    @State private var removingWorktreeID: UUID?

    var body: some View {
        List {
            Section {
                NavigationLink {
                    GitNewWorktreeView(viewModel: viewModel)
                } label: {
                    Label("New Worktree", systemImage: "plus")
                }
            }

            if let worktrees = viewModel.worktrees {
                Section("Worktrees") {
                    ForEach(worktrees) { worktree in
                        Button {
                            select(worktree)
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    HStack {
                                        Text(worktree.name)
                                            .font(.headline)
                                        if worktree.isPrimary {
                                            Image(systemName: "star.fill")
                                                .foregroundStyle(.yellow)
                                        }
                                    }
                                    Text(worktree.branch)
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                    Text(worktree.path)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if selectedWorktreeID == worktree.id || removingWorktreeID == worktree.id {
                                    ProgressView()
                                }
                            }
                        }
                        .disabled(selectedWorktreeID != nil || removingWorktreeID != nil)
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
                }
            } else if viewModel.isLoadingWorktrees {
                ProgressView()
            }
        }
        .navigationTitle("Worktrees")
        .navigationBarTitleDisplayMode(.inline)
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

struct GitPullRequestView: View {
    let viewModel: GitViewModel
    let pullRequest: VCSPullRequest
    @State private var method: VCSMergeMethod = .squash
    @State private var deleteBranch = true
    @State private var isMerging = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Form {
            Section {
                LabeledContent("Number", value: "#\(pullRequest.number)")
                LabeledContent("State", value: pullRequest.state.lowercased())
                LabeledContent("Base", value: pullRequest.baseBranch)
                if let checks = pullRequest.checks {
                    LabeledContent("Checks", value: checksLabel(checks))
                }
            }

            Section("Merge") {
                Picker("Method", selection: $method) {
                    ForEach(VCSMergeMethod.allCases, id: \.self) { method in
                        Text(method.rawValue.capitalized).tag(method)
                    }
                }
                Toggle("Delete branch", isOn: $deleteBranch)
                Button {
                    merge()
                } label: {
                    if isMerging {
                        ProgressView()
                    } else {
                        Text("Merge Pull Request")
                    }
                }
                .disabled(isMerging)
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Pull Request")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func merge() {
        isMerging = true
        Task {
            let didMerge = await viewModel.mergePullRequest(number: pullRequest.number, method: method, deleteBranch: deleteBranch)
            isMerging = false
            if didMerge {
                dismiss()
            }
        }
    }
}

struct GitCreatePullRequestView: View {
    let viewModel: GitViewModel
    @State private var title = ""
    @State private var bodyText = ""
    @State private var baseBranch = ""
    @State private var draft = false
    @State private var created: VCSPRCreated?
    @State private var isSubmitting = false

    var body: some View {
        Form {
            Section("Details") {
                TextField("Title", text: $title)
                TextField("Description", text: $bodyText, axis: .vertical)
                    .lineLimit(3...8)
                TextField(defaultBaseBranch, text: $baseBranch)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Toggle("Draft", isOn: $draft)
            }

            Section {
                Button {
                    submit()
                } label: {
                    if isSubmitting {
                        ProgressView()
                    } else {
                        Text("Create Pull Request")
                    }
                }
                .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSubmitting)
            }

            if let created, let url = URL(string: created.url) {
                Section {
                    Link("Open Pull Request #\(created.number)", destination: url)
                }
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("New Pull Request")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var defaultBaseBranch: String {
        viewModel.status?.defaultBranch ?? "main"
    }

    private func submit() {
        isSubmitting = true
        Task {
            created = await viewModel.createPullRequest(
                title: title,
                body: bodyText,
                baseBranch: baseBranch.isEmpty ? nil : baseBranch,
                draft: draft
            )
            isSubmitting = false
        }
    }
}

struct GitNewWorktreeView: View {
    let viewModel: GitViewModel
    @State private var name = ""
    @State private var branch = ""
    @State private var createBranch = true
    @State private var isSubmitting = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Form {
            Section("Worktree") {
                TextField("Name", text: $name)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Branch", text: $branch)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Toggle("Create branch", isOn: $createBranch)
            }

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
        }
        .navigationTitle("New Worktree")
        .navigationBarTitleDisplayMode(.inline)
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

struct GitDiffView: View {
    let viewModel: GitViewModel
    let filePath: String
    @State private var wrapsLines = false

    var body: some View {
        Group {
            if let diff = viewModel.diffsByPath[filePath] {
                diffContent(diff)
            } else if viewModel.loadingDiffPaths.contains(filePath) {
                ProgressView()
            } else {
                ContentUnavailableView {
                    Label("No Diff", systemImage: "doc.text")
                }
            }
        }
        .navigationTitle(fileName(filePath))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let diff = viewModel.diffsByPath[filePath], !diff.isBinary {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        wrapsLines.toggle()
                    } label: {
                        Image(systemName: wrapsLines ? "arrow.left.and.right.text.vertical" : "arrow.left.and.right")
                    }
                    .accessibilityLabel(wrapsLines ? "Disable line wrap" : "Enable line wrap")
                }
            }
        }
        .task {
            if viewModel.diffsByPath[filePath] == nil {
                await viewModel.loadDiff(filePath: filePath)
            }
        }
    }

    @ViewBuilder
    private func diffContent(_ diff: VCSDiff) -> some View {
        if diff.isBinary {
            ContentUnavailableView {
                Label("Binary File", systemImage: "doc")
            } description: {
                Text("No preview is available.")
            }
        } else {
            GitCodeDiffViewer(
                diff: diff,
                filePath: filePath,
                wrapsLines: wrapsLines,
                isLoading: viewModel.loadingDiffPaths.contains(filePath),
                onLoadFull: { Task { await viewModel.loadDiff(filePath: filePath, forceFull: true) } }
            )
        }
    }
}

struct GitCodeDiffViewer: View {
    let diff: VCSDiff
    let filePath: String
    let wrapsLines: Bool
    let isLoading: Bool
    let onLoadFull: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider()
            if diff.rows.isEmpty {
                ContentUnavailableView {
                    Label("No Changes", systemImage: "doc.text")
                }
            } else {
                GeometryReader { proxy in
                    ScrollView(.vertical) {
                        ScrollView(.horizontal) {
                            LazyVStack(alignment: .leading, spacing: 0) {
                                ForEach(Array(diff.rows.enumerated()), id: \.offset) { index, row in
                                    GitDiffRowView(row: row, wrapsLine: wrapsLines)
                                        .id(index)
                                }
                            }
                            .frame(minWidth: wrapsLines ? proxy.size.width : max(proxy.size.width, 760), alignment: .leading)
                        }
                    }
                    .background(Color(.systemBackground))
                }
            }
        }
        .background(Color(.systemBackground))
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: "doc.text")
                .foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 3) {
                Text(fileName(filePath))
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text(filePath)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            HStack(spacing: 8) {
                Text("+\(diff.additions)")
                    .foregroundStyle(.green)
                Text("-\(diff.deletions)")
                    .foregroundStyle(.red)
            }
            .font(.caption.monospacedDigit().weight(.semibold))
            if diff.truncated {
                Button {
                    onLoadFull()
                } label: {
                    if isLoading {
                        ProgressView()
                    } else {
                        Text("Full")
                    }
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .disabled(isLoading)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color(.secondarySystemBackground))
    }
}

struct GitFileRow: View {
    let file: GitFile
    let isStaged: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(fileName(file.path))
                Text(file.path)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                Text(statusLabel(file.status))
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(statusColor(file.status).opacity(0.18))
                    .clipShape(Capsule())
                if !isStaged {
                    Text("unstaged")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

struct GitDiffRowView: View {
    let row: VCSDiffRow
    let wrapsLine: Bool

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            Rectangle()
                .fill(rowAccent(row.kind))
                .frame(width: 3)
            Text(rowMarker(row.kind))
                .frame(width: 22, alignment: .center)
                .foregroundStyle(rowAccent(row.kind))
            Text(lineNumber(row.oldLineNumber))
                .frame(width: 42, alignment: .trailing)
                .padding(.trailing, 8)
                .foregroundStyle(.secondary)
                .textSelection(.enabled)
            Text(lineNumber(row.newLineNumber))
                .frame(width: 42, alignment: .trailing)
                .padding(.trailing, 10)
                .foregroundStyle(.secondary)
                .textSelection(.enabled)
            Text(row.text)
                .foregroundStyle(rowForeground(row.kind))
                .lineLimit(wrapsLine ? nil : 1)
                .fixedSize(horizontal: !wrapsLine, vertical: true)
                .textSelection(.enabled)
                .padding(.trailing, 20)
            if wrapsLine {
                Spacer(minLength: 0)
            }
        }
        .font(.system(size: 12, weight: row.kind == .hunk ? .semibold : .regular, design: .monospaced))
        .padding(.vertical, row.kind == .hunk ? 7 : 4)
        .background(rowBackground(row.kind))
    }
}

private func fileName(_ path: String) -> String {
    URL(fileURLWithPath: path).lastPathComponent
}

private func lineNumber(_ value: Int?) -> String {
    value.map(String.init) ?? ""
}

private func statusLabel(_ status: GitFileStatus) -> String {
    switch status {
    case .added:
        return "A"
    case .modified:
        return "M"
    case .deleted:
        return "D"
    case .renamed:
        return "R"
    case .copied:
        return "C"
    case .untracked:
        return "?"
    case .unmerged:
        return "!"
    }
}

private func statusColor(_ status: GitFileStatus) -> Color {
    switch status {
    case .added, .copied:
        return .green
    case .modified, .renamed:
        return .orange
    case .deleted, .unmerged:
        return .red
    case .untracked:
        return .blue
    }
}

private func rowMarker(_ kind: VCSDiffRowKind) -> String {
    switch kind {
    case .addition:
        return "+"
    case .deletion:
        return "-"
    case .hunk:
        return "@"
    case .collapsed:
        return "..."
    case .context:
        return ""
    }
}

private func rowAccent(_ kind: VCSDiffRowKind) -> Color {
    switch kind {
    case .addition:
        return .green
    case .deletion:
        return .red
    case .hunk:
        return .blue
    case .collapsed:
        return .secondary
    case .context:
        return .clear
    }
}

private func rowForeground(_ kind: VCSDiffRowKind) -> Color {
    switch kind {
    case .hunk:
        return .blue
    case .collapsed:
        return .secondary
    default:
        return .primary
    }
}

private func rowBackground(_ kind: VCSDiffRowKind) -> Color {
    switch kind {
    case .addition:
        return Color.green.opacity(0.10)
    case .deletion:
        return Color.red.opacity(0.10)
    case .hunk:
        return Color.blue.opacity(0.12)
    case .collapsed:
        return Color.secondary.opacity(0.10)
    case .context:
        return Color(.systemBackground)
    }
}

private func checksLabel(_ checks: VCSPRChecks) -> String {
    if checks.failing > 0 {
        return "\(checks.failing) failing"
    }
    if checks.pending > 0 {
        return "\(checks.pending) pending"
    }
    if checks.total > 0 {
        return "\(checks.passing)/\(checks.total) passing"
    }
    return checks.status.rawValue
}
