import Foundation

struct SavedDevice: Codable, Identifiable {
    var id: String { "\(host):\(port)" }
    let name: String
    let host: String
    let port: UInt16
}

enum SavedDevicesStore {
    private static let devicesKey = "savedDevices"

    static func load() -> [SavedDevice] {
        guard let data = UserDefaults.standard.data(forKey: devicesKey),
              let devices = try? JSONDecoder().decode([SavedDevice].self, from: data)
        else { return [] }
        return devices
    }

    static func save(_ devices: [SavedDevice]) {
        guard let data = try? JSONEncoder().encode(devices) else { return }
        UserDefaults.standard.set(data, forKey: devicesKey)
    }
}
