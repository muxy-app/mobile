import ExpoModulesCore
import Foundation

struct SSHConnectionConfigRecord: Record {
  @Field var connectionId = ""
  @Field var host = ""
  @Field var port = 22
  @Field var username = ""
  @Field var auth: [String: String] = [:]
  @Field var cols = 80
  @Field var rows = 24
  @Field var termType = "xterm-256color"
  @Field var knownHostFingerprint: String?
}

enum SSHAuthentication: Sendable {
  case password(String)
  case privateKey(key: String, passphrase: String?)
}

struct SSHConnectionConfiguration: Sendable {
  let connectionId: String
  let host: String
  let port: Int
  let username: String
  let authentication: SSHAuthentication
  let cols: Int
  let rows: Int
  let termType: String
  let knownHostFingerprint: String?

  init(record: SSHConnectionConfigRecord) throws {
    let host = record.host.trimmingCharacters(in: .whitespacesAndNewlines)
    let username = record.username.trimmingCharacters(in: .whitespacesAndNewlines)
    let termType = record.termType.trimmingCharacters(in: .whitespacesAndNewlines)
    let connectionId = record.connectionId.trimmingCharacters(in: .whitespacesAndNewlines)

    guard !connectionId.isEmpty, connectionId.count <= 128 else {
      throw MuxySSHError.invalidConnectionId
    }
    guard Self.isValidHost(host) else { throw MuxySSHError.invalidHost }
    guard (1...65535).contains(record.port) else { throw MuxySSHError.invalidPort }
    guard !username.isEmpty else { throw MuxySSHError.invalidUsername }
    guard Self.isValidTermType(termType) else { throw MuxySSHError.invalidTermType }
    guard (1...10_000).contains(record.cols), (1...10_000).contains(record.rows) else {
      throw MuxySSHError.invalidTerminalSize
    }

    self.connectionId = connectionId
    self.host = host
    self.port = record.port
    self.username = username
    self.authentication = try Self.makeAuthentication(record.auth)
    self.cols = record.cols
    self.rows = record.rows
    self.termType = termType
    self.knownHostFingerprint = try Self.normalizedFingerprint(record.knownHostFingerprint)
  }

  private static func makeAuthentication(_ auth: [String: String]) throws -> SSHAuthentication {
    switch auth["type"] {
    case "password":
      guard let password = auth["password"], !password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
        throw MuxySSHError.missingCredentials
      }
      return .password(password)
    case "privateKey":
      guard let key = auth["privateKey"], !key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
        throw MuxySSHError.missingCredentials
      }
      let passphrase = auth["passphrase"].flatMap {
        $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0
      }
      return .privateKey(key: key, passphrase: passphrase)
    default:
      throw MuxySSHError.invalidAuthenticationType
    }
  }

  private static func normalizedFingerprint(_ fingerprint: String?) throws -> String? {
    guard let fingerprint else { return nil }
    let normalized = fingerprint.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    guard normalized.count == 64, normalized.allSatisfy(\.isHexDigit) else {
      throw MuxySSHError.invalidHostFingerprint
    }
    return normalized
  }

  private static func isValidHost(_ host: String) -> Bool {
    guard !host.isEmpty, !host.contains(" ") else { return false }
    let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-:")
    return host.unicodeScalars.allSatisfy(allowed.contains)
  }

  private static func isValidTermType(_ termType: String) -> Bool {
    guard !termType.isEmpty, termType.count <= 64 else { return false }
    let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-")
    return termType.unicodeScalars.allSatisfy(allowed.contains)
  }
}
