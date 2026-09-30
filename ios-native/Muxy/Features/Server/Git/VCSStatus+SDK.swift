import Foundation
import MuxyMobile

nonisolated extension VCSStatus {
    init(_ status: GitStatus) {
        let summary = status.summary
        self.init(
            branch: Self.displayBranch(of: summary),
            aheadCount: Int(clamping: summary.ahead),
            behindCount: Int(clamping: summary.behind),
            hasUpstream: summary.upstream != nil,
            stagedFiles: status.files.compactMap { VCSFile(staged: $0.file) },
            changedFiles: status.files.compactMap { VCSFile(unstaged: $0.file) },
            defaultBranch: status.defaultBranch,
            pullRequest: status.pullRequest.map(VCSPullRequest.init)
        )
    }

    private static func displayBranch(of summary: GitSummary) -> String {
        if let branch = summary.branch { return branch }
        guard let head = summary.head else { return "No branch" }
        return "Detached at \(head.prefix(7))"
    }
}

nonisolated extension VCSFile {
    init?(staged file: GitFile) {
        guard let change = file.staged else { return nil }
        self.init(path: file.path, status: VCSFileStatus(change), isUntracked: false)
    }

    init?(unstaged file: GitFile) {
        guard let change = file.unstaged else { return nil }
        self.init(path: file.path, status: VCSFileStatus(change), isUntracked: change == .untracked)
    }
}

nonisolated extension VCSFileStatus {
    init(_ change: GitChangeKind) {
        switch change {
        case .added: self = .added
        case .modified: self = .modified
        case .deleted: self = .deleted
        case .renamed: self = .renamed
        case .copied: self = .copied
        case .typeChanged: self = .typeChanged
        case .untracked: self = .untracked
        case .conflicted: self = .unmerged
        case .other: self = .other
        }
    }
}

nonisolated extension VCSPullRequest {
    init(_ pullRequest: GitPullRequest) {
        self.init(
            url: pullRequest.url,
            number: Int(clamping: pullRequest.number),
            state: pullRequest.state,
            isDraft: pullRequest.draft,
            baseBranch: pullRequest.baseBranch,
            mergeable: pullRequest.mergeable,
            mergeStateStatus: VCSPRMergeStateStatus(rawValue: pullRequest.mergeState.uppercased()),
            checks: VCSPRChecks(pullRequest.checks),
            headOid: pullRequest.headOid
        )
    }
}

nonisolated extension VCSPRChecks {
    init(_ checks: GitChecks) {
        let passing = Int(checks.passing)
        let failing = Int(checks.failing)
        let pending = Int(checks.pending)
        self.init(
            status: Self.status(passing: passing, failing: failing, pending: pending),
            passing: passing,
            failing: failing,
            pending: pending,
            total: passing + failing + pending
        )
    }

    private static func status(passing: Int, failing: Int, pending: Int) -> VCSPRChecksStatus {
        if failing > 0 { return .failure }
        if pending > 0 { return .pending }
        if passing > 0 { return .success }
        return .none
    }
}

nonisolated extension VCSPRCreated {
    init(_ pullRequest: GitPullRequest) {
        self.init(url: pullRequest.url, number: Int(clamping: pullRequest.number))
    }
}

nonisolated extension VCSBranches {
    init(_ branches: [GitBranch]) {
        self.init(
            current: branches.first { $0.current }?.name ?? "",
            locals: branches.map(\.name),
            defaultBranch: branches.first { $0.default }?.name
        )
    }
}

nonisolated extension VCSDiff {
    init(_ diff: GitDiff, path: String) {
        self.init(
            filePath: path,
            rows: diff.rows.map(VCSDiffRow.init),
            additions: Int(clamping: diff.additions),
            deletions: Int(clamping: diff.deletions),
            truncated: diff.truncated,
            isBinary: diff.binary
        )
    }
}

nonisolated extension VCSDiffRow {
    init(_ row: GitDiffRow) {
        self.init(
            kind: VCSDiffRowKind(row.kind),
            oldLineNumber: row.oldLineNumber.map { Int(clamping: $0) },
            newLineNumber: row.newLineNumber.map { Int(clamping: $0) },
            text: row.text
        )
    }
}

nonisolated extension VCSDiffRowKind {
    init(_ kind: GitDiffKind) {
        switch kind {
        case .hunk: self = .hunk
        case .context: self = .context
        case .addition: self = .addition
        case .deletion: self = .deletion
        }
    }
}

nonisolated extension GitMergeMethod {
    init(_ method: VCSMergeMethod) {
        switch method {
        case .merge: self = .merge
        case .squash: self = .squash
        case .rebase: self = .rebase
        }
    }
}
