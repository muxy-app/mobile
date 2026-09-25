import Foundation

nonisolated struct ProjectListItem: Identifiable, Equatable, Sendable {
    enum Icon: Equatable, Sendable {
        case symbol(String)
        case emoji(String)
    }

    let id: String
    let name: String
    let path: String
    let icon: Icon
    let iconColor: String?
    let logo: Data?
    let isNested: Bool
}

nonisolated enum ProjectListStatus: Equatable, Sendable {
    case loading
    case loadFailed
    case empty
    case disconnected(message: String?)
}
