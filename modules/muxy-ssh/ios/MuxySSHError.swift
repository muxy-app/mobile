import ExpoModulesCore
import Foundation

enum MuxySSHError: Error, Equatable, Sendable {
  case unreachable
  case authenticationFailed
  case hostKeyChanged
  case hostKeyRejected
  case keyParseFailed
  case missingCredentials
  case invalidAuthenticationType
  case invalidConnectionId
  case invalidHost
  case invalidPort
  case invalidUsername
  case invalidTermType
  case invalidTerminalSize
  case invalidHostFingerprint
  case invalidData
  case sessionNotFound
  case noPendingHostKey
  case disconnected

  var code: String {
    switch self {
    case .unreachable:
      return "E_SSH_UNREACHABLE"
    case .authenticationFailed:
      return "E_SSH_AUTHENTICATION_FAILED"
    case .hostKeyChanged:
      return "E_SSH_HOST_KEY_CHANGED"
    case .hostKeyRejected:
      return "E_SSH_HOST_KEY_REJECTED"
    case .keyParseFailed:
      return "E_SSH_KEY_PARSE_FAILED"
    case .missingCredentials:
      return "E_SSH_MISSING_CREDENTIALS"
    case .invalidAuthenticationType:
      return "E_SSH_INVALID_AUTHENTICATION_TYPE"
    case .invalidConnectionId:
      return "E_SSH_INVALID_CONNECTION_ID"
    case .invalidHost:
      return "E_SSH_INVALID_HOST"
    case .invalidPort:
      return "E_SSH_INVALID_PORT"
    case .invalidUsername:
      return "E_SSH_INVALID_USERNAME"
    case .invalidTermType:
      return "E_SSH_INVALID_TERM_TYPE"
    case .invalidTerminalSize:
      return "E_SSH_INVALID_TERMINAL_SIZE"
    case .invalidHostFingerprint:
      return "E_SSH_INVALID_HOST_FINGERPRINT"
    case .invalidData:
      return "E_SSH_INVALID_DATA"
    case .sessionNotFound:
      return "E_SSH_SESSION_NOT_FOUND"
    case .noPendingHostKey:
      return "E_SSH_NO_PENDING_HOST_KEY"
    case .disconnected:
      return "E_SSH_DISCONNECTED"
    }
  }

  var message: String {
    switch self {
    case .unreachable:
      return "Couldn't reach the server. Check the host and port."
    case .authenticationFailed:
      return "Authentication failed. Check your username and credentials."
    case .hostKeyChanged:
      return "The server's host key has changed. Connection refused to protect against tampering."
    case .hostKeyRejected:
      return "The server's host key was not trusted."
    case .keyParseFailed:
      return "Couldn't read the private key. Check the key and passphrase."
    case .missingCredentials:
      return "Missing SSH credentials."
    case .invalidAuthenticationType:
      return "The SSH authentication type is invalid."
    case .invalidConnectionId:
      return "The SSH connection identifier is invalid."
    case .invalidHost:
      return "The SSH host is invalid."
    case .invalidPort:
      return "The SSH port must be between 1 and 65535."
    case .invalidUsername:
      return "The SSH username is required."
    case .invalidTermType:
      return "The terminal type is invalid."
    case .invalidTerminalSize:
      return "The terminal size is invalid."
    case .invalidHostFingerprint:
      return "The stored host fingerprint is invalid."
    case .invalidData:
      return "The SSH input is not valid base64 data."
    case .sessionNotFound:
      return "The SSH session no longer exists."
    case .noPendingHostKey:
      return "The SSH session has no host key awaiting approval."
    case .disconnected:
      return "The SSH session was disconnected."
    }
  }
}

final class MuxySSHException: Exception {
  init(_ error: MuxySSHError) {
    super.init(
      name: "MuxySSHException",
      description: error.message,
      code: error.code
    )
  }
}
