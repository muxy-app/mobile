import Foundation
import Testing
@testable import Muxy

@MainActor
struct DeviceTabCatalogTests {
    private func waitUntil(timeout: Duration = .seconds(3), _ condition: () async -> Bool) async throws {
        let deadline = ContinuousClock.now + timeout
        while ContinuousClock.now < deadline {
            if await condition() { return }
            try await Task.sleep(for: .milliseconds(20))
        }
        Issue.record("Condition not met within \(timeout)")
    }

    private func tabs(of projectID: UUID, in catalog: DeviceTabCatalog) -> [Tab] {
        catalog.workspaces[projectID].map { WorkspaceFlattening.tabAreas(in: $0).flatMap(\.tabs) } ?? []
    }

    @Test func closingATabRemovesItRightAwayAndOnTheDevice() async throws {
        let keychain = InMemoryKeychainStore()
        try keychain.setToken("demo", for: DemoConnection.id)
        let manager = ConnectionManager(makeTransport: { _ in MockTransport() })
        let catalog = DeviceTabCatalog(connection: DemoConnection.connection, keychain: keychain, makeManager: { manager })
        catalog.start()
        try await waitUntil { catalog.hasLoaded }
        let project = try #require(catalog.projects.first { $0.name == "Muxy" })
        let nvim = try #require(tabs(of: project.id, in: catalog).first { $0.title == "nvim" })

        catalog.closeTab(nvim.id, in: project.id)

        #expect(!tabs(of: project.id, in: catalog).contains { $0.id == nvim.id })
        try await waitUntil {
            let result = try? await manager.request(.getWorkspace, params: GetWorkspaceParams(projectID: project.id.uuidString))
            guard let workspace = try? result?.decode(Workspace.self) else { return false }
            return !WorkspaceFlattening.tabAreas(in: workspace).flatMap(\.tabs).contains { $0.id == nvim.id }
        }
        catalog.stop()
    }
}
