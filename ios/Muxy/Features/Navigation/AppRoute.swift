import Foundation

enum AppRoute: Hashable {
    case projects(Connection)
    case projectDetail(connection: Connection, project: Project, tabID: UUID? = nil)
    case serverProjects(Connection)
    case serverProject(connection: Connection, projectID: String, tabID: UUID? = nil)
    case sshTerminal(Connection)

    var serverID: String? {
        switch self {
        case let .serverProjects(connection), let .serverProject(connection, _, _):
            return connection.serverRouteID
        case .projects, .projectDetail, .sshTerminal:
            return nil
        }
    }

    var destination: AppRoute {
        switch self {
        case let .projectDetail(connection, project, _):
            return .projectDetail(connection: connection, project: project)
        case let .serverProject(connection, projectID, _):
            return .serverProject(connection: connection, projectID: projectID)
        case .projects, .serverProjects, .sshTerminal:
            return self
        }
    }

    func hasSameDestination(as other: AppRoute) -> Bool {
        switch (self, other) {
        case let (.projectDetail(connection, project, _), .projectDetail(otherConnection, otherProject, _)):
            return connection.id == otherConnection.id && project.id == otherProject.id
        case let (.serverProject(connection, projectID, _), .serverProject(otherConnection, otherProjectID, _)):
            return connection.id == otherConnection.id && projectID == otherProjectID
        default:
            return self == other
        }
    }
}

extension [AppRoute] {
    func opening(_ route: AppRoute) -> [AppRoute] {
        guard let index = lastIndex(where: { $0.hasSameDestination(as: route) }) else { return self + [route] }
        return Array(self[...index])
    }
}
