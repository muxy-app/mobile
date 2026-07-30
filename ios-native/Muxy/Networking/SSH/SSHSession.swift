import Citadel
import Foundation
import NIOCore
import NIOSSH
import OSLog

actor SSHSession {
    private let connection: Connection
    private let keychain: KeychainStore

    private var client: SSHClient?
    private var ptyTask: Task<Void, Never>?
    private var stdinWriter: TTYStdinWriter?
    private var closeContinuation: CheckedContinuation<Void, Never>?

    private var state: SSHConnectionState = .idle {
        didSet { broadcastState() }
    }

    private var outputContinuation: AsyncStream<Data>.Continuation?
    private var stateContinuations: [UUID: AsyncStream<SSHConnectionState>.Continuation] = [:]
    private var pendingResize: (cols: Int, rows: Int)?

    init(connection: Connection, keychain: KeychainStore) {
        self.connection = connection
        self.keychain = keychain
    }

    func output() -> AsyncStream<Data> {
        AsyncStream { continuation in
            outputContinuation = continuation
        }
    }

    func stateUpdates() -> AsyncStream<SSHConnectionState> {
        AsyncStream { continuation in
            let id = UUID()
            stateContinuations[id] = continuation
            continuation.yield(state)
            continuation.onTermination = { [weak self] _ in
                Task { await self?.removeStateContinuation(id) }
            }
        }
    }

    func connect(cols: Int, rows: Int) async {
        guard state != .connected, state != .connecting else { return }
        state = .connecting

        do {
            let method = try makeAuthentication()
            let validator = SSHHostKeyValidator.custom(TOFUHostKeyValidator(connectionID: connection.id, keychain: keychain))
            let client = try await SSHClient.connect(
                host: connection.host,
                port: connection.port,
                authenticationMethod: method,
                hostKeyValidator: validator,
                reconnect: .never
            )
            self.client = client
            state = .connected
            startPTY(cols: cols, rows: rows)
        } catch let error as SSHError {
            state = .failed(error)
        } catch {
            state = .failed(mapped(error))
        }
    }

    func send(_ data: Data) {
        guard let stdinWriter else { return }
        let buffer = ByteBuffer(bytes: data)
        Task {
            do {
                try await stdinWriter.write(buffer)
            } catch {
                Log.ssh.error("ssh write failed: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    func resize(cols: Int, rows: Int) {
        guard let stdinWriter else {
            pendingResize = (cols, rows)
            return
        }
        Task {
            do {
                try await stdinWriter.changeSize(cols: cols, rows: rows, pixelWidth: 0, pixelHeight: 0)
            } catch {
                Log.ssh.error("ssh resize failed: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    func close() async {
        ptyTask?.cancel()
        ptyTask = nil
        resumeClose()
        outputContinuation?.finish()
        outputContinuation = nil
        stdinWriter = nil
        if let client {
            try? await client.close()
        }
        client = nil
        guard state == .connected || state == .connecting else { return }
        state = .disconnected
    }

    private func startPTY(cols: Int, rows: Int) {
        guard let client else { return }
        let request = SSHChannelRequestEvent.PseudoTerminalRequest(
            wantReply: true,
            term: "xterm-256color",
            terminalCharacterWidth: cols,
            terminalRowHeight: rows,
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
                    await self?.waitForClose()
                }
            } catch {
                await self?.ptyEnded(error: error)
            }
        }
    }

    private func ptyStarted(writer: TTYStdinWriter) {
        stdinWriter = writer
        guard let resizeTarget = pendingResize else { return }
        pendingResize = nil
        resize(cols: resizeTarget.cols, rows: resizeTarget.rows)
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
        guard let bytes = readable.readBytes(length: readable.readableBytes), !bytes.isEmpty else { return }
        outputContinuation?.yield(Data(bytes))
    }

    private func waitForClose() async {
        await withCheckedContinuation { continuation in
            closeContinuation = continuation
        }
    }

    private func ptyEnded(error: Error) {
        guard state == .connected else { return }
        Log.ssh.error("ssh pty ended: \(error.localizedDescription, privacy: .public)")
        state = .disconnected
        outputContinuation?.finish()
        outputContinuation = nil
        stdinWriter = nil
    }

    private func resumeClose() {
        closeContinuation?.resume()
        closeContinuation = nil
    }

    private func makeAuthentication() throws -> SSHAuthenticationMethod {
        guard let config = connection.sshConfig else { throw SSHError.missingCredentials }
        let secretKind: KeychainSecret = config.authMethod == .password ? .sshPassword : .sshPrivateKey
        guard let secret = (try? keychain.secret(secretKind, for: connection.id)) ?? nil else {
            throw SSHError.missingCredentials
        }
        let passphrase = (try? keychain.secret(.sshPassphrase, for: connection.id)) ?? nil
        return try SSHAuthenticationFactory.make(config: config, secret: secret, passphrase: passphrase)
    }

    private func mapped(_ error: Error) -> SSHError {
        let description = String(describing: error).lowercased()
        if description.contains("connect") || description.contains("refused") || description.contains("timeout") || description.contains("unreachable") {
            return .unreachable
        }
        return .authenticationFailed
    }

    private func broadcastState() {
        for continuation in stateContinuations.values {
            continuation.yield(state)
        }
    }

    private func removeStateContinuation(_ id: UUID) {
        stateContinuations[id] = nil
    }
}
