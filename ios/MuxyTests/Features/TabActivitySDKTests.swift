import Foundation
import MuxyMobile
import Testing
@testable import Muxy

struct TabActivitySDKTests {
    private func agent(_ session: UInt64, _ state: AgentState, provider: String = "Claude Code") -> Agent {
        Agent(sessionId: session, projectId: "muxy", provider: provider, state: state)
    }

    private func event(_ id: UInt64, session: UInt64, kind: ActivityKind, at timestamp: UInt64, provider: String = "Codex") -> ActivityEvent {
        ActivityEvent(id: id, sessionId: session, projectId: "muxy", provider: provider, timestamp: timestamp, kind: kind)
    }

    @Test func agentStateWinsOverEvents() {
        #expect(TabActivity.resolve(agent: agent(1, .blocked), event: nil).state == .waiting)
        #expect(TabActivity.resolve(agent: agent(1, .working), event: event(1, session: 1, kind: .completed, at: 1)).state == .working)
    }

    @Test func eventsDecideIdleAgents() {
        #expect(TabActivity.resolve(agent: agent(1, .idle), event: event(1, session: 1, kind: .completed, at: 1)).state == .finished)
        #expect(TabActivity.resolve(agent: nil, event: event(1, session: 1, kind: .attention, at: 1)).state == .waiting)
        #expect(TabActivity.resolve(agent: agent(1, .idle), event: nil).state == .idle)
    }

    @Test func providerPrefersTheAgentAndTimeComesFromTheEvent() {
        let activity = TabActivity.resolve(agent: agent(1, .idle), event: event(1, session: 1, kind: .completed, at: 1_700_000_000))

        #expect(activity.provider == "Claude Code")
        #expect(activity.since == Date(timeIntervalSince1970: 1_700_000_000))
        #expect(TabActivity.resolve(agent: nil, event: event(1, session: 1, kind: .completed, at: 1)).provider == "Codex")
    }

    @Test func bySessionKeepsTheLatestEventPerSession() {
        let activity = Activity(
            agents: [agent(1, .idle)],
            events: [
                event(1, session: 1, kind: .attention, at: 10),
                event(2, session: 1, kind: .completed, at: 20),
                event(3, session: 2, kind: .attention, at: 5),
            ]
        )

        let sessions = TabActivity.bySession(activity)

        #expect(sessions[1]?.state == .finished)
        #expect(sessions[2]?.state == .waiting)
        #expect(sessions.count == 2)
    }
}
