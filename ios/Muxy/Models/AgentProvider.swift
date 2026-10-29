import Foundation

nonisolated enum AgentProvider: CaseIterable, Sendable {
    case claude
    case codex
    case openCode
    case cursor
    case copilot
    case droid
    case pi
    case grok
    case kiro
    case xal
    case antigravity
    case ohMyPi

    init?(name: String) {
        let key = name.lowercased().filter { $0.isLetter || $0.isNumber }
        guard let provider = Self.aliases[key] else { return nil }
        self = provider
    }

    var displayName: String {
        switch self {
        case .claude: "Claude Code"
        case .codex: "Codex"
        case .openCode: "OpenCode"
        case .cursor: "Cursor"
        case .copilot: "Copilot"
        case .droid: "Droid"
        case .pi: "Pi"
        case .grok: "Grok"
        case .kiro: "Kiro"
        case .xal: "Xal"
        case .antigravity: "Antigravity"
        case .ohMyPi: "Oh My Pi"
        }
    }

    var iconName: String {
        switch self {
        case .claude: "provider-claude"
        case .codex: "provider-codex"
        case .openCode: "provider-opencode"
        case .cursor: "provider-cursor"
        case .copilot: "provider-copilot"
        case .droid: "provider-factory"
        case .pi: "provider-pi"
        case .grok: "provider-grok"
        case .kiro: "provider-kiro"
        case .xal: "provider-xal"
        case .antigravity: "provider-antigravity"
        case .ohMyPi: "provider-omp"
        }
    }

    private static let aliases: [String: AgentProvider] = [
        "claude": .claude,
        "claudecode": .claude,
        "codex": .codex,
        "opencode": .openCode,
        "cursor": .cursor,
        "copilot": .copilot,
        "githubcopilot": .copilot,
        "droid": .droid,
        "factory": .droid,
        "pi": .pi,
        "grok": .grok,
        "kiro": .kiro,
        "xal": .xal,
        "antigravity": .antigravity,
        "ohmypi": .ohMyPi,
        "omp": .ohMyPi,
    ]
}
