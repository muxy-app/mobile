import Foundation
import OSLog

final class UserDefaultsDeviceStore: DeviceStore {
    private let defaults: UserDefaults
    private let key: String

    init(defaults: UserDefaults = .standard, key: String = "muxy.devices") {
        self.defaults = defaults
        self.key = key
    }

    func load() -> [Device] {
        guard let data = defaults.data(forKey: key) else { return [] }
        do {
            return try JSONDecoder().decode([Device].self, from: data)
        } catch {
            Log.persistence.error("Failed to decode devices: \(error.localizedDescription, privacy: .public)")
            return []
        }
    }

    func save(_ devices: [Device]) {
        do {
            let data = try JSONEncoder().encode(devices)
            defaults.set(data, forKey: key)
        } catch {
            Log.persistence.error("Failed to encode devices: \(error.localizedDescription, privacy: .public)")
        }
    }

    func upsert(_ device: Device) {
        var devices = load()
        if let index = devices.firstIndex(where: { $0.id == device.id }) {
            devices[index] = device
        } else {
            devices.append(device)
        }
        save(devices)
    }

    func delete(id: Device.ID) {
        var devices = load()
        devices.removeAll { $0.id == id }
        save(devices)
    }
}
