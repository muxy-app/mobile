import Citadel
import Foundation
import NIOCore
import NIOSSH
import OSLog

actor MuxySSHSession {
  private static let logger = Logger(subsystem: "com.muxy.app", category: "ssh")

  let id: String

  private let configuration: SSHConnectionConfiguration
  private let eventSink: @Sendable (SSHModuleEvent) -> Void
  private let hostKeyApproval: SSHHostKeyApproval

  private var client: SSHClient?
  private var ptyTask: Task<Void, Never>?
  private var ptyStartContinuation: CheckedContinuation<Void, Error>?
  private var stdinWriter: TTYStdinWriter?
  private var pendingResize: (cols: Int, rows: Int)?
  private var state = SSHConnectionState.idle
  private var didClose = false

  init(
    id: String,
    configuration: SSHConnectionConfiguration,
    eventSink: @escaping @Sendable (SSHModuleEvent) -> Void
  ) {
    self.id = id
    self.configuration = configuration
    self.eventSink = eventSink
    self.hostKeyApproval = SSHHostKeyApproval(
      sessionId: id,
      connectionId: configuration.connectionId,
      knownFingerprint: configuration.knownHostFingerprint,
      eventSink: eventSink
    )
  }

  func connect(startPTY: Bool = true) async throws {
    guard state == .idle else { throw MuxySSHError.disconnected }
    updateState(.connecting)

    do {
      let authentication = try SSHAuthenticationFactory.make(configuration: configuration)
      let connectedClient = try await SSHClient.connect(
        host: configuration.host,
        port: configuration.port,
        authenticationMethod: authentication,
        hostKeyValidator: .custom(hostKeyApproval),
        reconnect: .never
      )

      guard state == .connecting else {
        try? await connectedClient.close()
        throw MuxySSHError.disconnected
      }

      client = connectedClient

      if startPTY {
        try await startPTYSession(client: connectedClient)
      } else {
        updateState(.connected)
      }
    } catch {
      let mappedError = Self.map(error)
      if state != .disconnected {
        updateState(.failed, error: mappedError)
      }
      throw mappedError
    }
  }

  func write(_ data: Data) async throws {
    guard state == .connected, let stdinWriter else {
      throw MuxySSHError.disconnected
    }
    do {
      try await stdinWriter.write(ByteBuffer(bytes: data))
    } catch {
      Self.logger.error("SSH write failed: \(error.localizedDescription, privacy: .public)")
      throw MuxySSHError.disconnected
    }
  }

  func resize(cols: Int, rows: Int) async throws {
    guard (1...10_000).contains(cols), (1...10_000).contains(rows) else {
      throw MuxySSHError.invalidTerminalSize
    }
    if state == .connecting {
      pendingResize = (cols, rows)
      return
    }
    guard state == .connected else { throw MuxySSHError.disconnected }
    guard let stdinWriter else {
      pendingResize = (cols, rows)
      return
    }

    do {
      try await stdinWriter.changeSize(
        cols: cols,
        rows: rows,
        pixelWidth: 0,
        pixelHeight: 0
      )
    } catch {
      Self.logger.error("SSH resize failed: \(error.localizedDescription, privacy: .public)")
      throw MuxySSHError.disconnected
    }
  }

  func respondToHostKey(accept: Bool) throws {
    guard hostKeyApproval.respond(accept: accept) else {
      throw MuxySSHError.noPendingHostKey
    }
  }

  func disconnect() async {
    guard !didClose else { return }

    hostKeyApproval.cancel()
    ptyTask?.cancel()
    ptyTask = nil
    failPTYStart(MuxySSHError.disconnected)
    stdinWriter = nil
    pendingResize = nil

    let connectedClient = client
    self.client = nil

    if state != .failed, state != .disconnected {
      updateState(.disconnected)
    }
    emitClosed(reason: nil)

    if let connectedClient {
      try? await connectedClient.close()
    }
  }

  private func startPTYSession(client: SSHClient) async throws {
    try await withCheckedThrowingContinuation { continuation in
      ptyStartContinuation = continuation
      let request = SSHChannelRequestEvent.PseudoTerminalRequest(
        wantReply: true,
        term: configuration.termType,
        terminalCharacterWidth: configuration.cols,
        terminalRowHeight: configuration.rows,
        terminalPixelWidth: 0,
        terminalPixelHeight: 0,
        terminalModes: SSHTerminalModes([:])
      )

      ptyTask = Task { [weak self] in
        do {
          try await client.withPTY(request) { inbound, outbound in
            await self?.ptyStarted(writer: outbound)
            for try await chunk in inbound {
              await self?.handle(chunk)
            }
          }
          await self?.ptyEnded(error: nil)
        } catch {
          await self?.ptyEnded(error: error)
        }
      }
    }
  }

  private func ptyStarted(writer: TTYStdinWriter) async {
    guard state == .connecting else { return }
    stdinWriter = writer
    updateState(.connected)
    succeedPTYStart()

    guard let pendingResize else { return }
    self.pendingResize = nil
    try? await resize(cols: pendingResize.cols, rows: pendingResize.rows)
  }

  private func handle(_ chunk: ExecCommandOutput) {
    let buffer: ByteBuffer
    switch chunk {
    case let .stdout(data):
      buffer = data
    case let .stderr(data):
      buffer = data
    }

    var readable = buffer
    guard let bytes = readable.readBytes(length: readable.readableBytes), !bytes.isEmpty else {
      return
    }

    eventSink(
      .data(
        connectionId: configuration.connectionId,
        sessionId: id,
        dataBase64: Data(bytes).base64EncodedString()
      )
    )
  }

  private func ptyEnded(error: Error?) async {
    guard !didClose else { return }

    if let error, !(error is CancellationError) {
      Self.logger.error("SSH PTY ended: \(error.localizedDescription, privacy: .public)")
    }

    stdinWriter = nil
    ptyTask = nil

    let connectedClient = client
    self.client = nil

    if let connectedClient {
      try? await connectedClient.close()
    }

    guard !didClose else { return }
    if state == .connecting {
      failPTYStart(error.map(Self.map) ?? MuxySSHError.disconnected)
      return
    }
    if state != .failed, state != .disconnected {
      updateState(.disconnected)
    }
    emitClosed(reason: error.map { Self.map($0).message })
  }

  private func succeedPTYStart() {
    guard let continuation = ptyStartContinuation else { return }
    ptyStartContinuation = nil
    continuation.resume()
  }

  private func failPTYStart(_ error: Error) {
    guard let continuation = ptyStartContinuation else { return }
    ptyStartContinuation = nil
    continuation.resume(throwing: error)
  }

  private func updateState(
    _ state: SSHConnectionState,
    error: MuxySSHError? = nil
  ) {
    self.state = state
    eventSink(
      .state(
        connectionId: configuration.connectionId,
        sessionId: id,
        state: state,
        error: error
      )
    )
  }

  private func emitClosed(reason: String?) {
    guard !didClose else { return }
    didClose = true
    eventSink(
      .closed(
        connectionId: configuration.connectionId,
        sessionId: id,
        reason: reason
      )
    )
  }

  private static func map(_ error: Error) -> MuxySSHError {
    if let error = error as? MuxySSHError {
      return error
    }

    let description = String(describing: error).lowercased()
    if description.contains("connect")
      || description.contains("refused")
      || description.contains("timeout")
      || description.contains("unreachable") {
      return .unreachable
    }
    return .authenticationFailed
  }
}
