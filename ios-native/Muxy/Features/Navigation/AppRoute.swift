import Foundation

enum AppRoute: Hashable {
    case projects(Connection)
    case projectDetail(connection: Connection, project: Project)
    case serverProjects(Connection)
    case serverProject(connection: Connection, projectID: String)
    case sshTerminal(Connection)

    var serverID: String? {
        switch self {
        case let .serverProjects(connection), let .serverProject(connection, _):
            return connection.serverID
        case .projects, .projectDetail, .sshTerminal:
            return nil
        }
    }
}
