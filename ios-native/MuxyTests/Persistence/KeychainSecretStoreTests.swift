import Foundation
import Testing
@testable import Muxy

struct KeychainSecretStoreTests {
    private func makeStore() -> KeychainTokenStore {
        KeychainTokenStore(service: "com.muxy.app.tests.\(UUID().uuidString)")
    }

    @Test func storesSecretKindsIndependently() throws {
        let store = makeStore()
        let id = UUID()
        try store.setSecret("pw", .sshPassword, for: id)
        try store.setSecret("key", .sshPrivateKey, for: id)
        try store.setSecret("phrase", .sshPassphrase, for: id)

        #expect(try store.secret(.sshPassword, for: id) == "pw")
        #expect(try store.secret(.sshPrivateKey, for: id) == "key")
        #expect(try store.secret(.sshPassphrase, for: id) == "phrase")
        try store.deleteSecrets(for: id)
    }

    @Test func tokenConvenienceMatchesTokenSecret() throws {
        let store = makeStore()
        let id = UUID()
        try store.setToken("t", for: id)
        #expect(try store.secret(.token, for: id) == "t")
        #expect(try store.token(for: id) == "t")
        try store.deleteSecrets(for: id)
    }

    @Test func deleteSecretsRemovesEveryKind() throws {
        let store = makeStore()
        let id = UUID()
        try store.setSecret("t", .token, for: id)
        try store.setSecret("pw", .sshPassword, for: id)
        try store.setSecret("fp", .sshHostKey, for: id)

        try store.deleteSecrets(for: id)

        #expect(try store.secret(.token, for: id) == nil)
        #expect(try store.secret(.sshPassword, for: id) == nil)
        #expect(try store.secret(.sshHostKey, for: id) == nil)
    }

    @Test func secretsAreIsolatedByConnectionID() throws {
        let store = makeStore()
        let first = UUID()
        let second = UUID()
        try store.setSecret("a", .sshPassword, for: first)
        try store.setSecret("b", .sshPassword, for: second)
        #expect(try store.secret(.sshPassword, for: first) == "a")
        #expect(try store.secret(.sshPassword, for: second) == "b")
        try store.deleteSecrets(for: first)
        try store.deleteSecrets(for: second)
    }

    @Test func deleteSecretsForOneConnectionLeavesOtherIntact() throws {
        let store = makeStore()
        let first = UUID()
        let second = UUID()
        try store.setSecret("a", .sshPassword, for: first)
        try store.setSecret("b", .sshPassword, for: second)

        try store.deleteSecrets(for: first)

        #expect(try store.secret(.sshPassword, for: first) == nil)
        #expect(try store.secret(.sshPassword, for: second) == "b")
        try store.deleteSecrets(for: second)
    }
}
