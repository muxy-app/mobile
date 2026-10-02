import Foundation

nonisolated enum SplitDirection: String, Codable, Sendable {
    case horizontal
    case vertical
}

nonisolated struct TabArea: Codable, Sendable, Identifiable, Equatable {
    let id: UUID
    let projectPath: String
    let tabs: [Tab]
    let activeTabID: UUID?
}

nonisolated struct WorkspaceSplit: Codable, Sendable, Equatable {
    let id: UUID
    let direction: SplitDirection
    let ratio: Double
    let first: Node
    let second: Node
}

nonisolated indirect enum Node: Codable, Sendable, Equatable {
    case tabArea(TabArea)
    case split(WorkspaceSplit)

    private enum CodingKeys: String, CodingKey {
        case type
        case tabArea
        case split
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let type = try container.decode(String.self, forKey: .type)
        switch type {
        case "tabArea":
            self = .tabArea(try container.decode(TabArea.self, forKey: .tabArea))
        case "split":
            self = .split(try container.decode(WorkspaceSplit.self, forKey: .split))
        default:
            throw DecodingError.dataCorruptedError(
                forKey: .type,
                in: container,
                debugDescription: "Unknown node type \(type)"
            )
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case let .tabArea(area):
            try container.encode("tabArea", forKey: .type)
            try container.encode(area, forKey: .tabArea)
        case let .split(split):
            try container.encode("split", forKey: .type)
            try container.encode(split, forKey: .split)
        }
    }
}

nonisolated struct Workspace: Codable, Sendable, Equatable {
    let projectID: UUID
    let worktreeID: UUID
    let focusedAreaID: UUID?
    let root: Node
}
