import Foundation

nonisolated enum TabKind: Codable, Sendable, Equatable {
    case terminal
    case vcs
    case unsupported(raw: String)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(String.self)
        switch raw {
        case "terminal":
            self = .terminal
        case "vcs":
            self = .vcs
        default:
            self = .unsupported(raw: raw)
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

    var rawValue: String {
        switch self {
        case .terminal:
            return "terminal"
        case .vcs:
            return "vcs"
        case let .unsupported(raw):
            return raw
        }
    }
}

nonisolated struct Tab: Codable, Sendable, Identifiable, Equatable {
    let id: UUID
    let kind: TabKind
    let title: String
    let isPinned: Bool
    let paneID: UUID?
}
