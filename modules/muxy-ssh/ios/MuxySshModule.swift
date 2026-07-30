import ExpoModulesCore
import Foundation

public final class MuxySshModule: Module {
  private let registry = SSHSessionRegistry()
  private lazy var eventEmitter = SSHEventEmitter(module: self)

  public func definition() -> ModuleDefinition {
    Name("MuxySsh")
    Events("onData", "onStateChange", "onClosed", "onHostKeyPrompt")

    Function("isAvailable") {
      true
    }

    AsyncFunction("connect") { (record: SSHConnectionConfigRecord) async throws -> String in
      let configuration = try SSHConnectionConfiguration(record: record)
      return try await createSession(configuration: configuration, startPTY: true)
    }

    AsyncFunction("write") { (sessionId: String, dataBase64: String) async throws in
      guard let data = Data(base64Encoded: dataBase64) else {
        throw MuxySSHException(.invalidData)
      }
      let session = try await session(id: sessionId)
      do {
        try await session.write(data)
      } catch {
        throw Self.exception(error)
      }
    }

    AsyncFunction("resize") { (sessionId: String, cols: Int, rows: Int) async throws in
      let session = try await session(id: sessionId)
      do {
        try await session.resize(cols: cols, rows: rows)
      } catch {
        throw Self.exception(error)
      }
    }

    AsyncFunction("disconnect") { (sessionId: String) async in
      guard let session = await registry.session(id: sessionId) else { return }
      await session.disconnect()
      await registry.remove(id: sessionId)
    }

    AsyncFunction("testConnection") { (record: SSHConnectionConfigRecord) async throws in
      let configuration = try SSHConnectionConfiguration(record: record)
      let sessionId = try await createSession(configuration: configuration, startPTY: false)
      guard let session = await registry.session(id: sessionId) else { return }
      await session.disconnect()
      await registry.remove(id: sessionId)
    }

    AsyncFunction("respondToHostKey") { (sessionId: String, accept: Bool) async throws in
      let session = try await session(id: sessionId)
      do {
        try await session.respondToHostKey(accept: accept)
      } catch {
        throw Self.exception(error)
      }
    }

    OnDestroy {
      let registry = self.registry
      Task {
        let sessions = await registry.takeAll()
        for session in sessions {
          await session.disconnect()
        }
      }
    }
  }

  private func createSession(
    configuration: SSHConnectionConfiguration,
    startPTY: Bool
  ) async throws -> String {
    let sessionId = UUID().uuidString.lowercased()
    let registry = self.registry
    let eventEmitter = self.eventEmitter
    let eventSink: @Sendable (SSHModuleEvent) -> Void = { event in
      eventEmitter.emit(event)
      if case let .closed(_, closedSessionId, _) = event {
        Task {
          await registry.remove(id: closedSessionId)
        }
      }
    }
    let session = MuxySSHSession(
      id: sessionId,
      configuration: configuration,
      eventSink: eventSink
    )

    await registry.insert(session)

    do {
      try await session.connect(startPTY: startPTY)
      return sessionId
    } catch {
      await session.disconnect()
      await registry.remove(id: sessionId)
      throw Self.exception(error)
    }
  }

  private func session(id: String) async throws -> MuxySSHSession {
    guard let session = await registry.session(id: id) else {
      throw MuxySSHException(.sessionNotFound)
    }
    return session
  }

  private static func exception(_ error: Error) -> MuxySSHException {
    if let error = error as? MuxySSHError {
      return MuxySSHException(error)
    }
    return MuxySSHException(.authenticationFailed)
  }
}

private final class SSHEventEmitter: @unchecked Sendable {
  private weak var module: MuxySshModule?

  init(module: MuxySshModule) {
    self.module = module
  }

  func emit(_ event: SSHModuleEvent) {
    DispatchQueue.main.async { [weak self] in
      guard let module = self?.module else { return }

      switch event {
      case let .data(connectionId, sessionId, dataBase64):
        module.sendEvent(
          "onData",
          [
            "connectionId": connectionId,
            "sessionId": sessionId,
            "dataBase64": dataBase64
          ]
        )
      case let .state(connectionId, sessionId, state, error):
        var payload: [String: Any] = [
          "connectionId": connectionId,
          "sessionId": sessionId,
          "state": state.rawValue
        ]
        if let error {
          payload["errorCode"] = error.code
          payload["errorMessage"] = error.message
        }
        module.sendEvent("onStateChange", payload)
      case let .closed(connectionId, sessionId, reason):
        var payload: [String: Any] = [
          "connectionId": connectionId,
          "sessionId": sessionId
        ]
        if let reason {
          payload["reason"] = reason
        }
        module.sendEvent("onClosed", payload)
      case let .hostKeyPrompt(connectionId, sessionId, fingerprint, keyType):
        module.sendEvent(
          "onHostKeyPrompt",
          [
            "connectionId": connectionId,
            "sessionId": sessionId,
            "fingerprint": fingerprint,
            "keyType": keyType
          ]
        )
      }
    }
  }
}
