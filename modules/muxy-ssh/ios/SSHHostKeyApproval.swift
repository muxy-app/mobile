import Crypto
import Foundation
import NIOConcurrencyHelpers
import NIOCore
import NIOSSH

final class SSHHostKeyApproval: NIOSSHClientServerAuthenticationDelegate, @unchecked Sendable {
  private struct PendingApproval {
    let promise: EventLoopPromise<Void>
  }

  private let sessionId: String
  private let connectionId: String
  private let knownFingerprint: String?
  private let eventSink: @Sendable (SSHModuleEvent) -> Void
  private let pendingApproval = NIOLockedValueBox<PendingApproval?>(nil)

  init(
    sessionId: String,
    connectionId: String,
    knownFingerprint: String?,
    eventSink: @escaping @Sendable (SSHModuleEvent) -> Void
  ) {
    self.sessionId = sessionId
    self.connectionId = connectionId
    self.knownFingerprint = knownFingerprint
    self.eventSink = eventSink
  }

  func validateHostKey(
    hostKey: NIOSSHPublicKey,
    validationCompletePromise: EventLoopPromise<Void>
  ) {
    let fingerprint = Self.fingerprint(of: hostKey)

    if let knownFingerprint {
      guard knownFingerprint == fingerprint else {
        validationCompletePromise.fail(MuxySSHError.hostKeyChanged)
        return
      }
      validationCompletePromise.succeed(())
      return
    }

    let didStore = pendingApproval.withLockedValue { pending in
      guard pending == nil else { return false }
      pending = PendingApproval(promise: validationCompletePromise)
      return true
    }

    guard didStore else {
      validationCompletePromise.fail(MuxySSHError.hostKeyChanged)
      return
    }

    eventSink(
      .hostKeyPrompt(
        connectionId: connectionId,
        sessionId: sessionId,
        fingerprint: fingerprint,
        keyType: Self.keyType(of: hostKey)
      )
    )
  }

  func respond(accept: Bool) -> Bool {
    let approval = pendingApproval.withLockedValue { pending in
      defer { pending = nil }
      return pending
    }
    guard let approval else { return false }

    if accept {
      approval.promise.succeed(())
    } else {
      approval.promise.fail(MuxySSHError.hostKeyRejected)
    }
    return true
  }

  func cancel() {
    let approval = pendingApproval.withLockedValue { pending in
      defer { pending = nil }
      return pending
    }
    approval?.promise.fail(MuxySSHError.disconnected)
  }

  private static func fingerprint(of hostKey: NIOSSHPublicKey) -> String {
    var buffer = ByteBuffer()
    _ = hostKey.write(to: &buffer)
    let bytes = buffer.readBytes(length: buffer.readableBytes) ?? []
    let digest = SHA256.hash(data: Data(bytes))
    return digest.map { String(format: "%02x", $0) }.joined()
  }

  private static func keyType(of hostKey: NIOSSHPublicKey) -> String {
    String(openSSHPublicKey: hostKey).split(separator: " ", maxSplits: 1).first.map(String.init) ?? "unknown"
  }
}
