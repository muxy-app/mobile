import Foundation

protocol DeviceStore: Sendable {
    func load() -> [Device]
    func save(_ devices: [Device])
    func upsert(_ device: Device)
    func delete(id: Device.ID)
}
