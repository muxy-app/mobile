import Foundation

final class InMemoryDeviceStore: DeviceStore, @unchecked Sendable {
    private let lock = NSLock()
    private var devices: [Device]

    init(devices: [Device] = []) {
        self.devices = devices
    }

    func load() -> [Device] {
        lock.lock()
        defer { lock.unlock() }
        return devices
    }

    func save(_ devices: [Device]) {
        lock.lock()
        self.devices = devices
        lock.unlock()
    }

    func upsert(_ device: Device) {
        lock.lock()
        if let index = devices.firstIndex(where: { $0.id == device.id }) {
            devices[index] = device
        } else {
            devices.append(device)
        }
        lock.unlock()
    }

    func delete(id: Device.ID) {
        lock.lock()
        devices.removeAll { $0.id == id }
        lock.unlock()
    }
}

final class InMemoryKeychainStore: KeychainStore, @unchecked Sendable {
    private let lock = NSLock()
    private var tokens: [Device.ID: String] = [:]

    func setToken(_ token: String, for deviceID: Device.ID) throws {
        lock.lock()
        tokens[deviceID] = token
        lock.unlock()
    }

    func token(for deviceID: Device.ID) throws -> String? {
        lock.lock()
        defer { lock.unlock() }
        return tokens[deviceID]
    }

    func deleteToken(for deviceID: Device.ID) throws {
        lock.lock()
        tokens[deviceID] = nil
        lock.unlock()
    }
}
