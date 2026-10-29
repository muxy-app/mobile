import Foundation
import MuxyMobile

extension TabActivity {
    nonisolated static func resolve(agent: Agent?, event: ActivityEvent?) -> TabActivity {
        TabActivity(
            state: state(agent: agent, event: event),
            provider: [agent?.provider, event?.provider].compactMap { $0 }.first { !$0.isEmpty },
            since: event.map { Date(timeIntervalSince1970: TimeInterval($0.timestamp)) }
        )
    }

    nonisolated static func bySession(_ activity: Activity) -> [UInt64: TabActivity] {
        let agents = Dictionary(activity.agents.map { ($0.sessionId, $0) }, uniquingKeysWith: { _, latest in latest })
        let events = Dictionary(activity.events.map { ($0.sessionId, $0) }, uniquingKeysWith: { first, second in
            first.timestamp >= second.timestamp ? first : second
        })
        let sessions = Set(agents.keys).union(events.keys)
        return Dictionary(uniqueKeysWithValues: sessions.map { id in
            (id, resolve(agent: agents[id], event: events[id]))
        })
    }

    private nonisolated static func state(agent: Agent?, event: ActivityEvent?) -> State {
        switch agent?.state {
        case .blocked: return .waiting
        case .working: return .working
        case .idle, .unknown, nil: break
        }
        switch event?.kind {
        case .attention: return .waiting
        case .completed: return .finished
        case nil: return .idle
        }
    }
}
