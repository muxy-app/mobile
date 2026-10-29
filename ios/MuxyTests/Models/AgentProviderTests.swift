import Testing
@testable import Muxy

struct AgentProviderTests {
    @Test(arguments: [
        ("Claude Code", AgentProvider.claude),
        ("claude-code", .claude),
        ("claude", .claude),
        ("Codex", .codex),
        ("OpenCode", .openCode),
        ("GitHub Copilot", .copilot),
        ("Factory", .droid),
        ("Droid", .droid),
        ("Oh My Pi", .ohMyPi),
        ("omp", .ohMyPi),
        ("Antigravity", .antigravity),
    ])
    func matchesKnownNames(name: String, provider: AgentProvider) {
        #expect(AgentProvider(name: name) == provider)
    }

    @Test func unknownNamesDoNotMatch() {
        #expect(AgentProvider(name: "Gemini CLI") == nil)
        #expect(AgentProvider(name: "") == nil)
    }

    @Test func everyProviderHasAnIcon() {
        for provider in AgentProvider.allCases {
            #expect(provider.iconName.hasPrefix("provider-"))
            #expect(!provider.displayName.isEmpty)
        }
    }

    @Test func activityUsesTheProviderDisplayName() {
        let known = TabActivity(state: .idle, provider: "claude", since: nil)
        let unknown = TabActivity(state: .idle, provider: "Gemini CLI", since: nil)

        #expect(known.providerName == "Claude Code")
        #expect(unknown.providerName == "Gemini CLI")
        #expect(TabActivity.none.providerName == nil)
    }

    @Test(arguments: [
        (TabActivity.State.idle, "Claude Code"),
        (.working, "Working"),
        (.waiting, "Waiting for you"),
        (.finished, "Finished"),
    ])
    func statusLabels(state: TabActivity.State, label: String) {
        #expect(TabActivity(state: state, provider: "Claude Code", since: nil).statusLabel == label)
    }
}
