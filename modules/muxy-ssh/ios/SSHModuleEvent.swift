import Foundation

enum SSHConnectionState: String, Sendable {
  case idle
  case connecting
  case connected
  case disconnected
  case failed
}

enum SSHModuleEvent: Sendable {
  case data(connectionId: String, sessionId: String, dataBase64: String)
  case state(connectionId: String, sessionId: String, state: SSHConnectionState, error: MuxySSHError?)
  case closed(connectionId: String, sessionId: String, reason: String?)
  case hostKeyPrompt(connectionId: String, sessionId: String, fingerprint: String, keyType: String)
}
