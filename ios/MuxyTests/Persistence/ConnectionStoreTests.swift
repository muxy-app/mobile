import Foundation
import Testing
@testable import Muxy

struct ConnectionStoreTests {
    private func makeStore() -> UserDefaultsConnectionStore {
        let suite = UserDefaults(suiteName: "muxy.tests.\(UUID().uuidString)")!
        return UserDefaultsConnectionStore(defaults: suite, key: "devices")
    }

    private func device(name: String = "Studio") -> Connection {
        Connection(
            id: UUID(),
            name: name,
            host: "studio.local",
            port: 4865,
            pairingState: .paired,
            serviceName: "Studio",
            discoverySource: .bonjour
        )
    }

    @Test func loadIsEmptyInitially() {
        #expect(makeStore().load().isEmpty)
    }

    @Test func upsertAddsDevice() {
        let store = makeStore()
        let device = device()
        store.upsert(device)
        #expect(store.load() == [device])
    }

    @Test func upsertReplacesByID() {
        let store = makeStore()
        var device = device(name: "Old")
        store.upsert(device)

        device.name = "New"
        store.upsert(device)

        let loaded = store.load()
        #expect(loaded.count == 1)
        #expect(loaded.first?.name == "New")
    }

    @Test func deleteRemovesByID() {
        let store = makeStore()
        let first = device(name: "First")
        let second = device(name: "Second")
        store.save([first, second])

        store.delete(id: first.id)
        let loaded = store.load()
        #expect(loaded == [second])
    }

    @Test func deviceCodableRoundTrips() throws {
        let device = device()
        let data = try JSONEncoder().encode(device)
        let decoded = try JSONDecoder().decode(Connection.self, from: data)
        #expect(decoded == device)
    }

    @Test func deviceWithoutServiceNameRoundTrips() throws {
        var device = device()
        device.serviceName = nil
        let data = try JSONEncoder().encode(device)
        let decoded = try JSONDecoder().decode(Connection.self, from: data)
        #expect(decoded.serviceName == nil)
        #expect(decoded == device)
    }
}
