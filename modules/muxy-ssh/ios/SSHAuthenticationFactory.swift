import Citadel
import Crypto
import Foundation

enum SSHAuthenticationFactory {
  static func make(configuration: SSHConnectionConfiguration) throws -> SSHAuthenticationMethod {
    switch configuration.authentication {
    case let .password(password):
      return .passwordBased(username: configuration.username, password: password)
    case let .privateKey(key, passphrase):
      return try privateKeyMethod(
        username: configuration.username,
        key: key,
        passphrase: passphrase
      )
    }
  }

  private static func privateKeyMethod(
    username: String,
    key: String,
    passphrase: String?
  ) throws -> SSHAuthenticationMethod {
    let decryptionKey = passphrase.flatMap { $0.data(using: .utf8) }
    let keyType = try? SSHKeyDetection.detectPrivateKeyType(from: key)

    if keyType == .ed25519 {
      guard let parsed = try? Curve25519.Signing.PrivateKey(
        sshEd25519: key,
        decryptionKey: decryptionKey
      ) else {
        throw MuxySSHError.keyParseFailed
      }
      return .ed25519(username: username, privateKey: parsed)
    }

    if keyType == .rsa {
      guard let parsed = try? Insecure.RSA.PrivateKey(
        sshRsa: key,
        decryptionKey: decryptionKey
      ) else {
        throw MuxySSHError.keyParseFailed
      }
      return .rsa(username: username, privateKey: parsed)
    }

    throw MuxySSHError.keyParseFailed
  }
}
