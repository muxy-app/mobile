import Foundation

enum AppRoute: Hashable {
    case projects(Connection)
    case projectDetail(connection: Connection, project: Project)
    case sshTerminal(Connection)
}
