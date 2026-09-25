import Foundation
import MuxyMobile
import Testing
@testable import Muxy

struct KeychainCredentialStoreTests {
    private func makeStore() -> KeychainCredentialStore {
        KeychainCredentialStore(service: "com.muxy.app.tests.servers.\(UUID().uuidString)")
    }

    @Test func savesAndReadsEveryField() throws {
        let store = makeStore()
        let credential = Fixtures.credential()
        try store.save(credential)
        #expect(try store.credential(serverId: credential.serverId) == credential)
        try store.delete(serverId: credential.serverId)
    }

    @Test func pairingTheSameComputerAgainReplacesItsCredential() throws {
        let store = makeStore()
        try store.save(Fixtures.credential(name: "Old Name"))
        try store.save(Fixtures.credential(name: "New Name"))
        let all = try store.all()
        #expect(all.count == 1)
        #expect(all.first?.serverName == "New Name")
        try store.delete(serverId: Fixtures.credential().serverId)
    }

    @Test func listsComputersSortedByName() throws {
        let store = makeStore()
        try store.save(Fixtures.credential(id: "b", name: "Workstation"))
        try store.save(Fixtures.credential(id: "a", name: "Laptop"))
        #expect(try store.all().map(\.serverName) == ["Laptop", "Workstation"])
        try store.delete(serverId: "a")
        try store.delete(serverId: "b")
    }

    @Test func deleteForgetsTheComputer() throws {
        let store = makeStore()
        try store.save(Fixtures.credential())
        try store.delete(serverId: Fixtures.credential().serverId)
        #expect(try store.credential(serverId: Fixtures.credential().serverId) == nil)
        #expect(try store.all().isEmpty)
    }

    @Test func deletingAnUnknownComputerDoesNotThrow() throws {
        try makeStore().delete(serverId: "missing")
    }

    @Test func storedCredentialRoundTripsThroughJSON() throws {
        let credential = Fixtures.credential()
        let data = try JSONEncoder().encode(StoredCredential(credential))
        #expect(try JSONDecoder().decode(StoredCredential.self, from: data).credential == credential)
    }
}
