import Foundation

struct VCSStatusDTO: Codable {
    let branch: String
    let aheadCount: Int
    let behindCount: Int
    let hasUpstream: Bool
    let stagedFiles: [GitFileDTO]
    let changedFiles: [GitFileDTO]
    let defaultBranch: String?
    let pullRequest: VCSPullRequestDTO?
}

struct GitFileDTO: Identifiable, Codable, Hashable {
    var id: String { path }
    let path: String
    let status: GitFileStatusDTO
    let isUntracked: Bool

    init(path: String, status: GitFileStatusDTO, isUntracked: Bool = false) {
        self.path = path
        self.status = status
        self.isUntracked = isUntracked
    }
}

enum GitFileStatusDTO: String, Codable {
    case added
    case modified
    case deleted
    case renamed
    case copied
    case untracked
    case unmerged
}

struct VCSPullRequestDTO: Codable, Hashable {
    let url: String
    let number: Int
    let state: String
    let isDraft: Bool
    let baseBranch: String
}

struct VCSBranchesDTO: Codable {
    let current: String
    let locals: [String]
    let defaultBranch: String?
}

struct VCSCreatePRResultDTO: Codable {
    let url: String
    let number: Int
}
