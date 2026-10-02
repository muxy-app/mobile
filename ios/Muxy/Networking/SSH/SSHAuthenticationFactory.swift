import Citadel
import Crypto
import Foundation

nonisolated enum SSHAuthenticationFactory {
    static func make(config: SSHConfig, secret: String, passphrase: String?) throws -> SSHAuthenticationMethod {
        switch config.authMethod {
        case .password:
            return .passwordBased(username: config.username, password: secret)
        case .privateKey:
            return try privateKeyMethod(username: config.username, key: secret, passphrase: passphrase)
        }
    }

    private static func privateKeyMethod(username: String, key: String, passphrase: String?) throws -> SSHAuthenticationMethod {
        let decryptionKey = passphrase.flatMap { $0.data(using: .utf8) }
        let keyType = try? SSHKeyDetection.detectPrivateKeyType(from: key)

        if keyType == .ed25519 {
            guard let parsed = try? Curve25519.Signing.PrivateKey(sshEd25519: key, decryptionKey: decryptionKey) else {
                throw SSHError.keyParseFailed
            }
            return .ed25519(username: username, privateKey: parsed)
        }

        if keyType == .rsa {
            guard let parsed = try? Insecure.RSA.PrivateKey(sshRsa: key, decryptionKey: decryptionKey) else {
                throw SSHError.keyParseFailed
            }
            return .rsa(username: username, privateKey: parsed)
        }

        throw SSHError.keyParseFailed
    }
}
