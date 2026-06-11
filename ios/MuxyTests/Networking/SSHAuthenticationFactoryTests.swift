import Testing
@testable import Muxy

struct SSHAuthenticationFactoryTests {
    @Test func passwordAuthenticationDoesNotThrow() throws {
        let config = SSHConfig(username: "root", authMethod: .password)
        _ = try SSHAuthenticationFactory.make(config: config, secret: "hunter2", passphrase: nil)
    }

    @Test func invalidPrivateKeyThrowsKeyParseFailed() {
        let config = SSHConfig(username: "root", authMethod: .privateKey)
        #expect(throws: SSHError.keyParseFailed) {
            try SSHAuthenticationFactory.make(config: config, secret: "not-a-key", passphrase: nil)
        }
    }

    @Test func emptyPrivateKeyThrowsKeyParseFailed() {
        let config = SSHConfig(username: "root", authMethod: .privateKey)
        #expect(throws: SSHError.keyParseFailed) {
            try SSHAuthenticationFactory.make(config: config, secret: "", passphrase: nil)
        }
    }

    @Test func truncatedPEMPrivateKeyThrowsKeyParseFailed() {
        let config = SSHConfig(username: "root", authMethod: .privateKey)
        let truncated = "-----BEGIN OPENSSH PRIVATE KEY-----\nnotbase64\n-----END OPENSSH PRIVATE KEY-----"
        #expect(throws: SSHError.keyParseFailed) {
            try SSHAuthenticationFactory.make(config: config, secret: truncated, passphrase: nil)
        }
    }
}
