import Foundation
import Testing
@testable import Muxy

struct DemoBackendTests {
    @Test func authenticateReturnsPairing() async throws {
        let backend = DemoBackend()
        let result = try await backend.authenticate()
        let pairing = try result.decode(PairingResult.self)
        let clientID = await backend.currentClientID

        #expect(result.type == ResultType.pairing)
        #expect(pairing.deviceName == "Demo Desktop")
        #expect(UUID(uuidString: pairing.clientID) == clientID)
    }

    @Test func listProjectsReturnsSampleProjects() async throws {
        let backend = DemoBackend()
        let result = try await backend.request(.listProjects, params: Optional<EmptyRequestParams>.none)
        let projects = try result.decode(ProjectsResult.self).projects

        #expect(result.type == ResultType.projects)
        #expect(projects.map(\.name) == ["Muxy", "Web App"])
    }

    @Test func workspaceLoadsForSampleProject() async throws {
        let backend = DemoBackend()
        let project = try await backend.request(.listProjects, params: Optional<EmptyRequestParams>.none)
            .decode(ProjectsResult.self)
            .projects
            .first!
        let result = try await backend.request(.getWorkspace, params: GetWorkspaceParams(projectID: project.id.uuidString))
        let workspace = try result.decode(Workspace.self)

        #expect(result.type == ResultType.workspace)
        #expect(workspace.projectID == project.id)
        #expect(WorkspaceFlattening.focusedTabArea(in: workspace)?.tabs.first?.kind == .terminal)
    }

    @Test func takeOverPaneEmitsOwnershipAndSnapshot() async throws {
        let backend = DemoBackend()
        let project = try await backend.request(.listProjects, params: Optional<EmptyRequestParams>.none)
            .decode(ProjectsResult.self)
            .projects
            .first!
        let workspace = try await backend.request(.getWorkspace, params: GetWorkspaceParams(projectID: project.id.uuidString))
            .decode(Workspace.self)
        let paneID = WorkspaceFlattening.focusedTabArea(in: workspace)!.tabs.first!.paneID!

        let events = try await backend.events(for: .takeOverPane, params: TakeOverPaneParams(paneID: paneID.uuidString, cols: 80, rows: 24))

        let snapshot = try events[1].data?.decode(TerminalBytesEvent.self)

        #expect(events.map(\.event) == [EventName.paneOwnershipChanged, EventName.terminalSnapshot])
        #expect(snapshot.flatMap { String(data: $0.bytes, encoding: .utf8) }?.contains("Demo Mode") == true)
    }

    @MainActor
    @Test func demoConnectionApplyAddsAndRemovesConnectionAndToken() throws {
        let store = InMemoryConnectionStore()
        let keychain = InMemoryKeychainStore()

        DemoConnection.apply(enabled: true, store: store, keychain: keychain)

        #expect(store.load() == [DemoConnection.connection])
        #expect(try keychain.token(for: DemoConnection.id) == DemoConnection.token)

        DemoConnection.apply(enabled: false, store: store, keychain: keychain)

        #expect(store.load().isEmpty)
        #expect(try keychain.token(for: DemoConnection.id) == nil)
    }
}
