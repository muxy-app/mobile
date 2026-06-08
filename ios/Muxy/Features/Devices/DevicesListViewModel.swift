import Foundation
import Observation
import OSLog

@MainActor
@Observable
final class DevicesListViewModel {
    private(set) var devices: [Device] = []

    private let store: DeviceStore
    private let keychain: KeychainStore

    init(store: DeviceStore, keychain: KeychainStore) {
        self.store = store
        self.keychain = keychain
    }

    func load() {
        devices = store.load()
    }

    func delete(_ device: Device) {
        store.delete(id: device.id)
        do {
            try keychain.deleteToken(for: device.id)
        } catch {
            Log.persistence.error("Failed to delete token: \(error.localizedDescription, privacy: .public)")
        }
        load()
    }

    func delete(at offsets: IndexSet) {
        let targets = offsets.map { devices[$0] }
        targets.forEach(delete)
    }
}
