import SwiftUI

struct GitSheetView<Worktrees: View>: View {
    @State var viewModel: GitViewModel
    @ViewBuilder let worktrees: () -> Worktrees

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            GitOverviewView(viewModel: viewModel, worktrees: worktrees)
                .screenTitle("Git")
                .toolbar {
                    DoneToolbarItem { dismiss() }
                }
        }
        .presentationDetents([.large])
        .task {
            if viewModel.status == nil {
                await viewModel.refreshStatus()
            }
        }
    }
}

struct GitOverviewView<Worktrees: View>: View {
    let viewModel: GitViewModel
    @ViewBuilder let worktrees: () -> Worktrees

    var body: some View {
        CardList {
            if let status = viewModel.status {
                branchSection(status)
                actionSection(status)
                manageSection(status)
                changesSection(status)
            } else if viewModel.isLoadingStatus {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
            } else {
                ThemedEmptyState(
                    title: "No Git Information",
                    systemImage: "arrow.triangle.branch",
                    message: "Git status is not available for this project."
                )
            }

            if let errorMessage = viewModel.errorMessage {
                CardSection {
                    CardMessageRow(errorMessage)
                }
            }
        }
        .refreshable { await viewModel.refreshStatus() }
    }

    private func branchSection(_ status: VCSStatus) -> some View {
        CardSection {
            CardRow(status.branch, systemImage: "arrow.triangle.branch", subtitle: upstreamSummary(status)) {
                EmptyView()
            }
            .font(.headline)
            .accessibilityElement(children: .combine)
        }
    }

    private func upstreamSummary(_ status: VCSStatus) -> String {
        guard status.hasUpstream else { return "No upstream" }
        return "\(status.behindCount) behind · \(status.aheadCount) ahead"
    }

    private func actionSection(_ status: VCSStatus) -> some View {
        CardSection {
            Button {
                Task { await viewModel.pull() }
            } label: {
                CardRow("Pull", systemImage: "arrow.down") { EmptyView() }
            }
            .buttonStyle(.cardRow)
            .disabled(!status.hasUpstream)

            Button {
                Task { await viewModel.push() }
            } label: {
                CardRow(viewModel.pushTitle(for: status), systemImage: "arrow.up") { EmptyView() }
            }
            .buttonStyle(.cardRow)
            .disabled(!viewModel.canPush(status))

            NavigationLink {
                GitCommitView(viewModel: viewModel)
            } label: {
                CardRow(
                    "Commit",
                    systemImage: "checkmark.circle",
                    value: viewModel.totalChanges > 0 ? viewModel.totalChanges.formatted() : nil
                )
            }
            .buttonStyle(.cardRow)
            .disabled(viewModel.totalChanges == 0)
        }
    }

    private func manageSection(_ status: VCSStatus) -> some View {
        CardSection("Manage") {
            NavigationLink {
                GitBranchesView(viewModel: viewModel)
            } label: {
                CardRow("Branches", systemImage: "arrow.triangle.branch")
            }
            .buttonStyle(.cardRow)

            NavigationLink {
                worktrees()
            } label: {
                CardRow("Worktrees", systemImage: "square.3.layers.3d")
            }
            .buttonStyle(.cardRow)

            if let pullRequest = status.pullRequest, let url = URL(string: pullRequest.url) {
                NavigationLink {
                    GitPullRequestView(viewModel: viewModel, pullRequest: pullRequest)
                } label: {
                    CardRow("Pull Request #\(pullRequest.number)", systemImage: "arrow.triangle.pull")
                }
                .buttonStyle(.cardRow)

                Link(destination: url) {
                    CardRow("Open Pull Request", systemImage: "safari") {
                        Image(systemName: "arrow.up.right")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.tertiary)
                            .accessibilityHidden(true)
                    }
                }
                .buttonStyle(.cardRow)
            } else {
                NavigationLink {
                    GitCreatePullRequestView(viewModel: viewModel)
                } label: {
                    CardRow("New Pull Request", systemImage: "arrow.triangle.pull")
                }
                .buttonStyle(.cardRow)
            }
        }
    }

    @ViewBuilder
    private func changesSection(_ status: VCSStatus) -> some View {
        if viewModel.totalChanges == 0 {
            CardSection("Status") {
                CardRow("Working tree clean", systemImage: "checkmark.circle") { EmptyView() }
            }
        } else {
            CardSection("Changes", detail: viewModel.totalChanges.formatted(), separatorInset: CardMetrics.textInset) {
                ForEach(status.stagedFiles) { file in
                    changeRow(file, isStaged: true)
                }
                ForEach(status.changedFiles) { file in
                    changeRow(file, isStaged: false)
                }
            }
        }
    }

    private func changeRow(_ file: VCSFile, isStaged: Bool) -> some View {
        NavigationLink {
            GitDiffView(viewModel: viewModel, key: GitDiffKey(path: file.path, isStaged: isStaged))
        } label: {
            CardRow(fileName(file.path), subtitle: file.path) {
                EmptyView()
            } trailing: {
                GitFileStatusBadge(file: file, isStaged: isStaged)
                CardRowChevron()
            }
            .lineLimit(1)
            .truncationMode(.middle)
        }
        .buttonStyle(.cardRow)
    }
}

struct GitCommitView: View {
    let viewModel: GitViewModel
    @State private var message = ""
    @State private var stageAll = true
    @State private var isSubmitting = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme

    var body: some View {
        ThemedForm {
            Section {
                TextField("Describe your change", text: $message, axis: .vertical)
                    .lineLimit(3...8)
                    .foregroundStyle(theme.foreground)
            } header: {
                ThemedSectionHeader("Commit Message")
            }

            Section {
                Toggle("Stage all changes", isOn: $stageAll)
                    .foregroundStyle(theme.foreground)
            }

            FormSubmitSection("Commit", isSubmitting: isSubmitting, isDisabled: !canCommit, action: submit)

            if let status = viewModel.status {
                Section {
                    ForEach(status.stagedFiles) { file in
                        GitFileRow(file: file, isStaged: true)
                    }
                    ForEach(status.changedFiles) { file in
                        GitFileRow(file: file, isStaged: false)
                    }
                } header: {
                    ThemedSectionHeader("Changes")
                }
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(theme.red)
                }
            }
        }
        .screenTitle("Commit")
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
        CardList {
            CardSection {
                NavigationLink {
                    GitNewBranchView(viewModel: viewModel)
                } label: {
                    CardRow("New Branch", systemImage: "plus")
                }
                .buttonStyle(.cardRow)
            }

            if let branches = viewModel.branches {
                CardSection("Local", detail: branches.locals.count.formatted()) {
                    ForEach(branches.locals, id: \.self) { branch in
                        branchRow(branch, in: branches)
                    }
                }
            } else if viewModel.isLoadingBranches {
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
        .screenTitle("Branches")
        .task {
            if viewModel.branches == nil {
                await viewModel.refreshBranches()
            }
        }
        .refreshable { await viewModel.refreshBranches() }
    }

    private func branchRow(_ branch: String, in branches: VCSBranches) -> some View {
        let isCurrent = branch == branches.current
        return Button {
            switchBranch(branch)
        } label: {
            CardRow(
                branch,
                systemImage: "arrow.triangle.branch",
                value: branch == branches.defaultBranch ? "default" : nil
            ) {
                if switchingBranch == branch {
                    ProgressView()
                } else if isCurrent {
                    CardRowCheckmark()
                }
            }
            .lineLimit(1)
        }
        .buttonStyle(.cardRow)
        .disabled(switchingBranch != nil && !isCurrent)
        .accessibilityAddTraits(isCurrent ? .isSelected : [])
    }

    private func switchBranch(_ branch: String) {
        guard branch != viewModel.branches?.current else { return }
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
    @Environment(\.appTheme) private var theme

    var body: some View {
        ThemedForm {
            Section {
                TextField("feature/name", text: $name)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .foregroundStyle(theme.foreground)
            } header: {
                ThemedSectionHeader("Branch Name")
            }

            FormSubmitSection(
                "Create Branch",
                isSubmitting: isSubmitting,
                isDisabled: name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                action: submit
            )
        }
        .screenTitle("New Branch")
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

struct GitPullRequestView: View {
    let viewModel: GitViewModel
    let pullRequest: VCSPullRequest
    @State private var method: VCSMergeMethod = .squash
    @State private var deleteBranch = true
    @State private var isMerging = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme

    var body: some View {
        ThemedForm {
            Section {
                LabeledContent("Number", value: "#\(pullRequest.number)")
                LabeledContent("State", value: pullRequest.state.lowercased())
                LabeledContent("Base", value: pullRequest.baseBranch)
                if let checks = pullRequest.checks {
                    LabeledContent("Checks", value: checksLabel(checks))
                }
            }
            .foregroundStyle(theme.foreground)

            Section {
                Picker("Method", selection: $method) {
                    ForEach(VCSMergeMethod.allCases, id: \.self) { method in
                        Text(method.rawValue.capitalized).tag(method)
                    }
                }
                .foregroundStyle(theme.foreground)
                Toggle("Delete branch", isOn: $deleteBranch)
                    .foregroundStyle(theme.foreground)
            } header: {
                ThemedSectionHeader("Merge")
            }

            FormSubmitSection("Merge Pull Request", isSubmitting: isMerging, action: merge)

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(theme.red)
                }
            }
        }
        .screenTitle("Pull Request")
    }

    private func merge() {
        isMerging = true
        Task {
            let didMerge = await viewModel.mergePullRequest(pullRequest, method: method, deleteBranch: deleteBranch)
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
    @Environment(\.appTheme) private var theme

    var body: some View {
        ThemedForm {
            Section {
                TextField("Title", text: $title)
                TextField("Description", text: $bodyText, axis: .vertical)
                    .lineLimit(3...8)
                TextField(defaultBaseBranch, text: $baseBranch)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Toggle("Draft", isOn: $draft)
            } header: {
                ThemedSectionHeader("Details")
            }
            .foregroundStyle(theme.foreground)

            FormSubmitSection(
                "Create Pull Request",
                isSubmitting: isSubmitting,
                isDisabled: title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                action: submit
            )

            if let created, let url = URL(string: created.url) {
                Section {
                    Link("Open Pull Request #\(created.number)", destination: url)
                }
            }

            if let errorMessage = viewModel.errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(theme.red)
                }
            }
        }
        .screenTitle("New Pull Request")
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

struct GitDiffView: View {
    let viewModel: GitViewModel
    let key: GitDiffKey
    @Environment(\.appTheme) private var theme
    @State private var wrapsLines = false

    var body: some View {
        Group {
            if let diff = viewModel.diffs[key] {
                diffContent(diff)
            } else if viewModel.loadingDiffs.contains(key) {
                ProgressView()
            } else {
                ThemedEmptyState(
                    title: "No Diff",
                    systemImage: "doc.text",
                    message: "There is nothing to show for this file."
                )
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.groupedBackground)
        .screenTitle(fileName(key.path))
        .toolbar {
            if let diff = viewModel.diffs[key], !diff.isBinary {
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
            if viewModel.diffs[key] == nil {
                await viewModel.loadDiff(key)
            }
        }
    }

    @ViewBuilder
    private func diffContent(_ diff: VCSDiff) -> some View {
        if diff.isBinary {
            ThemedEmptyState(
                title: "Binary File",
                systemImage: "doc",
                message: "No preview is available."
            )
        } else {
            GitCodeDiffViewer(
                diff: diff,
                filePath: key.path,
                wrapsLines: wrapsLines,
                isLoading: viewModel.loadingDiffs.contains(key),
                onLoadFull: { Task { await viewModel.loadDiff(key, full: true) } }
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

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 0) {
            header
            theme.separator.frame(height: CardMetrics.separatorHeight)
            if diff.rows.isEmpty {
                ThemedEmptyState(
                    title: "No Changes",
                    systemImage: "doc.text",
                    message: "This diff has no line changes."
                )
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
                    .background(theme.groupedBackground)
                }
            }
        }
        .background(theme.groupedBackground)
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: "doc.text")
                .foregroundStyle(theme.secondaryForeground)
            VStack(alignment: .leading, spacing: 3) {
                Text(fileName(filePath))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(theme.foreground)
                    .lineLimit(1)
                Text(filePath)
                    .font(.caption)
                    .foregroundStyle(theme.secondaryForeground)
                    .lineLimit(1)
            }
            Spacer()
            HStack(spacing: 8) {
                Text("+\(diff.additions)")
                    .foregroundStyle(theme.green)
                Text("-\(diff.deletions)")
                    .foregroundStyle(theme.red)
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
                .buttonStyle(.glass)
                .controlSize(.small)
                .disabled(isLoading)
            }
        }
        .padding(.horizontal, PageMargin.horizontal)
        .padding(.vertical, 10)
        .background(theme.secondaryGroupedBackground)
    }
}

struct GitFileRow: View {
    let file: VCSFile
    let isStaged: Bool

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(fileName(file.path))
                    .foregroundStyle(theme.foreground)
                Text(file.path)
                    .font(.caption)
                    .foregroundStyle(theme.secondaryForeground)
                    .lineLimit(1)
            }
            Spacer()
            GitFileStatusBadge(file: file, isStaged: isStaged)
        }
    }
}

struct GitFileStatusBadge: View {
    let file: VCSFile
    let isStaged: Bool

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .trailing, spacing: 4) {
            Text(statusLabel(file.status))
                .font(.caption)
                .foregroundStyle(theme.foreground)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(statusColor(file.status, in: theme).opacity(0.18), in: Capsule())
            if !isStaged {
                Text("unstaged")
                    .font(.caption2)
                    .foregroundStyle(theme.secondaryForeground)
            }
        }
        .fixedSize()
    }
}

struct GitDiffRowView: View {
    let row: VCSDiffRow
    let wrapsLine: Bool

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            Rectangle()
                .fill(rowAccent(row.kind, in: theme))
                .frame(width: 3)
            Text(rowMarker(row.kind))
                .frame(width: 22, alignment: .center)
                .foregroundStyle(rowAccent(row.kind, in: theme))
            Text(lineNumber(row.oldLineNumber))
                .frame(width: 42, alignment: .trailing)
                .padding(.trailing, 8)
                .foregroundStyle(theme.secondaryForeground)
                .textSelection(.enabled)
            Text(lineNumber(row.newLineNumber))
                .frame(width: 42, alignment: .trailing)
                .padding(.trailing, 10)
                .foregroundStyle(theme.secondaryForeground)
                .textSelection(.enabled)
            Text(row.text)
                .foregroundStyle(rowForeground(row.kind, in: theme))
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
        .background(rowBackground(row.kind, in: theme))
    }
}

private func fileName(_ path: String) -> String {
    URL(fileURLWithPath: path).lastPathComponent
}

private func lineNumber(_ value: Int?) -> String {
    value.map(String.init) ?? ""
}

private func statusLabel(_ status: VCSFileStatus) -> String {
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
    case .typeChanged:
        return "T"
    case .untracked:
        return "?"
    case .unmerged:
        return "!"
    case .other:
        return "X"
    }
}

private func statusColor(_ status: VCSFileStatus, in theme: AppTheme) -> Color {
    switch status {
    case .added, .copied:
        return theme.green
    case .modified, .renamed, .typeChanged:
        return theme.yellow
    case .deleted, .unmerged:
        return theme.red
    case .untracked:
        return theme.cyan
    case .other:
        return theme.secondaryForeground
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

private func rowAccent(_ kind: VCSDiffRowKind, in theme: AppTheme) -> Color {
    switch kind {
    case .addition:
        return theme.green
    case .deletion:
        return theme.red
    case .hunk:
        return theme.cyan
    case .collapsed:
        return theme.secondaryForeground
    case .context:
        return .clear
    }
}

private func rowForeground(_ kind: VCSDiffRowKind, in theme: AppTheme) -> Color {
    switch kind {
    case .hunk:
        return theme.cyan
    case .collapsed:
        return theme.secondaryForeground
    default:
        return theme.foreground
    }
}

private func rowBackground(_ kind: VCSDiffRowKind, in theme: AppTheme) -> Color {
    switch kind {
    case .addition:
        return theme.green.opacity(0.10)
    case .deletion:
        return theme.red.opacity(0.10)
    case .hunk:
        return theme.cyan.opacity(0.12)
    case .collapsed:
        return theme.secondaryForeground.opacity(0.10)
    case .context:
        return theme.groupedBackground
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
