import Foundation
import Testing
@testable import Muxy

@MainActor
struct AppRouteTests {
    private let studio = Connection(id: UUID(), name: "Studio", host: "studio.local", port: 4865)
    private let laptop = Connection(id: UUID(), name: "Laptop", host: "laptop.local", port: 4865)
    private let muxy = Self.project(name: "Muxy")
    private let webApp = Self.project(name: "Web App")

    private static func project(name: String) -> Project {
        Project(
            id: UUID(),
            name: name,
            path: "/Users/demo/\(name)",
            sortOrder: 0,
            createdAt: "",
            icon: nil,
            logo: nil,
            iconColor: nil,
            preferredWorktreeParentPath: nil,
            worktreesEnabled: nil,
            workspaceKind: nil,
            workspaceID: nil,
            workspaceName: nil
        )
    }

    @Test func destinationDropsTheRequestedTab() {
        let tabID = UUID()

        #expect(AppRoute.projectDetail(connection: studio, project: muxy, tabID: tabID).destination == .projectDetail(connection: studio, project: muxy))
        #expect(AppRoute.serverProject(connection: studio, projectID: "muxy", tabID: tabID).destination == .serverProject(connection: studio, projectID: "muxy"))
    }

    @Test func destinationKeepsRoutesWithoutTabs() {
        #expect(AppRoute.projects(studio).destination == .projects(studio))
        #expect(AppRoute.serverProjects(studio).destination == .serverProjects(studio))
        #expect(AppRoute.sshTerminal(studio).destination == .sshTerminal(studio))
    }

    @Test func tabsOfTheSameProjectShareADestination() {
        let first = AppRoute.projectDetail(connection: studio, project: muxy, tabID: UUID())
        let second = AppRoute.projectDetail(connection: studio, project: muxy, tabID: UUID())

        #expect(first.hasSameDestination(as: second))
        #expect(first.destination == second.destination)
    }

    @Test func differentProjectsOrDevicesAreDifferentDestinations() {
        let route = AppRoute.projectDetail(connection: studio, project: muxy)

        #expect(!route.hasSameDestination(as: .projectDetail(connection: studio, project: webApp)))
        #expect(!route.hasSameDestination(as: .projectDetail(connection: laptop, project: muxy)))
        #expect(!AppRoute.serverProject(connection: studio, projectID: "muxy").hasSameDestination(as: .serverProject(connection: studio, projectID: "web")))
    }

    @Test func openingAProjectAlreadyInThePathCutsBackToIt() {
        let path: [AppRoute] = [.projects(studio), .projectDetail(connection: studio, project: muxy, tabID: UUID()), .projects(laptop)]

        let opened = path.opening(.projectDetail(connection: studio, project: muxy, tabID: UUID()))

        #expect(opened == Array(path.prefix(2)))
    }

    @Test func openingANewDestinationAppendsIt() {
        let path: [AppRoute] = [.projects(studio)]

        #expect(path.opening(.sshTerminal(laptop)) == [.projects(studio), .sshTerminal(laptop)])
    }
}
