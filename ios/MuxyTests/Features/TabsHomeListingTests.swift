import Foundation
import Testing
@testable import Muxy

struct TabsHomeListingTests {
    private func tab(_ title: String, state: TabActivity.State = .idle, since: Date? = nil) -> TabsHomeTab {
        TabsHomeTab(
            id: UUID(),
            title: title,
            detail: "~/Projects/muxy",
            kind: state == .idle ? .shell : .agent,
            activity: TabActivity(state: state, provider: state == .idle ? nil : "Claude Code", since: since)
        )
    }

    private func project(_ name: String, tabs: [TabsHomeTab]) -> TabsHomeProject {
        TabsHomeProject(id: name, name: name, path: "/Users/demo/\(name)", tabs: tabs, canCreate: true)
    }

    private func device(_ name: String, projects: [TabsHomeProject]) -> TabsHomeDevice {
        TabsHomeDevice(id: UUID(), name: name, systemImage: "desktopcomputer", status: .connected, projects: projects, canEdit: true)
    }

    @Test func emptyQueryKeepsEverything() {
        let mac = device("Mac", projects: [project("muxy", tabs: [tab("zsh")])])

        #expect(mac.matching("  ")?.projects.count == 1)
    }

    @Test func deviceNameMatchKeepsAllProjects() {
        let mac = device("Mac Studio", projects: [project("muxy", tabs: [tab("zsh")]), project("udesk", tabs: [])])

        #expect(mac.matching("studio")?.projects.count == 2)
    }

    @Test func tabMatchKeepsOnlyMatchingTabs() {
        let mac = device("Mac", projects: [
            project("muxy", tabs: [tab("zsh"), tab("pnpm dev")]),
            project("udesk", tabs: [tab("zsh")]),
        ])

        let matched = mac.matching("pnpm")

        #expect(matched?.projects.map(\.name) == ["muxy"])
        #expect(matched?.projects.first?.tabs.map(\.title) == ["pnpm dev"])
    }

    @Test func noMatchHidesTheDevice() {
        let mac = device("Mac", projects: [project("muxy", tabs: [tab("zsh")])])

        #expect(mac.matching("nothing") == nil)
    }

    @Test func attentionCollectsWaitingAndFinishedNewestFirst() {
        let older = tab("Review PR", state: .waiting, since: Date(timeIntervalSince1970: 100))
        let newer = tab("Fix tests", state: .finished, since: Date(timeIntervalSince1970: 200))
        let mac = device("Mac", projects: [project("muxy", tabs: [older, tab("zsh"), tab("Refactor", state: .working), newer])])

        let attention = TabsHomeAttention.collect(from: [mac])

        #expect(attention.map(\.tab.title) == ["Fix tests", "Review PR"])
        #expect(attention.first?.deviceName == "Mac")
        #expect(attention.first?.projectName == "muxy")
    }
}
