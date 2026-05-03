import Foundation

struct WorkspaceDTO: Codable {
    let projectID: UUID
    let worktreeID: UUID
    let focusedAreaID: UUID?
    let root: SplitNodeDTO
}

enum SplitDirectionDTO: String, Codable {
    case horizontal
    case vertical
}

indirect enum SplitNodeDTO: Codable {
    case tabArea(TabAreaDTO)
    case split(SplitBranchDTO)

    private enum CodingKeys: String, CodingKey {
        case type
        case tabArea
        case split
    }

    private enum NodeType: String, Codable {
        case tabArea
        case split
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let type = try container.decode(NodeType.self, forKey: .type)
        switch type {
        case .tabArea:
            self = try .tabArea(container.decode(TabAreaDTO.self, forKey: .tabArea))
        case .split:
            self = try .split(container.decode(SplitBranchDTO.self, forKey: .split))
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case let .tabArea(area):
            try container.encode(NodeType.tabArea, forKey: .type)
            try container.encode(area, forKey: .tabArea)
        case let .split(branch):
            try container.encode(NodeType.split, forKey: .type)
            try container.encode(branch, forKey: .split)
        }
    }
}

struct SplitBranchDTO: Codable {
    let id: UUID
    let direction: SplitDirectionDTO
    let ratio: Double
    let first: SplitNodeDTO
    let second: SplitNodeDTO
}

struct TabAreaDTO: Identifiable, Codable {
    let id: UUID
    let projectPath: String
    let tabs: [TabDTO]
    let activeTabID: UUID?
}

struct TabDTO: Identifiable, Codable {
    let id: UUID
    let kind: TabKindDTO
    let title: String
    let isPinned: Bool
    let paneID: UUID?

    init(
        id: UUID,
        kind: TabKindDTO,
        title: String,
        isPinned: Bool,
        paneID: UUID? = nil
    ) {
        self.id = id
        self.kind = kind
        self.title = title
        self.isPinned = isPinned
        self.paneID = paneID
    }
}

enum TabKindDTO: String, Codable {
    case terminal
    case vcs
    case editor
    case diffViewer
}
