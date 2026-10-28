import Foundation

nonisolated struct TabActivity: Equatable, Sendable {
    enum State: Equatable, Sendable {
        case idle
        case working
        case waiting
        case finished
    }

    static let none = TabActivity(state: .idle, provider: nil, since: nil)

    let state: State
    let provider: String?
    let since: Date?

    var isAgent: Bool {
        provider != nil
    }

    var needsAttention: Bool {
        state == .waiting || state == .finished
    }

    var providerName: String? {
        guard let provider else { return nil }
        return Self.knownProviders[provider.lowercased()] ?? provider.capitalized
    }

    var statusLabel: String? {
        switch state {
        case .idle: providerName
        case .working: "Working"
        case .waiting: "Waiting for you"
        case .finished: "Finished"
        }
    }

    private static let knownProviders = [
        "claude": "Claude Code",
        "claude-code": "Claude Code",
        "claudecode": "Claude Code",
        "codex": "Codex",
        "copilot": "Copilot",
        "cursor": "Cursor",
        "droid": "Droid",
        "grok": "Grok",
        "kiro": "Kiro",
        "opencode": "OpenCode",
        "antigravity": "Antigravity",
    ]
}
