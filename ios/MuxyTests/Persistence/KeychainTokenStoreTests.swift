import Foundation
import Testing
@testable import Muxy

struct KeychainTokenStoreTests {
    private func makeStore() -> KeychainTokenStore {
        KeychainTokenStore(service: "com.muxy.app.tests.\(UUID().uuidString)")
    }

    @Test func setThenGetReturnsToken() throws {
        let store = makeStore()
        let id = UUID()
        try store.setToken("secret-token", for: id)
        #expect(try store.token(for: id) == "secret-token")
        try store.deleteToken(for: id)
    }

    @Test func getMissingReturnsNil() throws {
        let store = makeStore()
        #expect(try store.token(for: UUID()) == nil)
    }

    @Test func setOverwritesExistingToken() throws {
        let store = makeStore()
        let id = UUID()
        try store.setToken("first", for: id)
        try store.setToken("second", for: id)
        #expect(try store.token(for: id) == "second")
        try store.deleteToken(for: id)
    }

    @Test func deleteRemovesToken() throws {
        let store = makeStore()
        let id = UUID()
        try store.setToken("token", for: id)
        try store.deleteToken(for: id)
        #expect(try store.token(for: id) == nil)
    }

    @Test func deleteMissingDoesNotThrow() throws {
        let store = makeStore()
        try store.deleteToken(for: UUID())
    }

    @Test func tokensAreIsolatedByDeviceID() throws {
        let store = makeStore()
        let first = UUID()
        let second = UUID()
        try store.setToken("token-a", for: first)
        try store.setToken("token-b", for: second)
        #expect(try store.token(for: first) == "token-a")
        #expect(try store.token(for: second) == "token-b")
        try store.deleteToken(for: first)
        try store.deleteToken(for: second)
    }
}
