import Foundation

enum AppRoute: Hashable {
    case projects(Device)
    case projectDetail(device: Device, project: Project)
}
