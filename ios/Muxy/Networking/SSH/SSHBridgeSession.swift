@preconcurrency import Citadel
import Foundation
import MuxyMobile
import NIOCore
import NIOPosix
@preconcurrency import NIOSSH
import OSLog

nonisolated final class SSHBridgeSession: @unchecked Sendable {
    private let lock = NSLock()
    private var parent: (any Channel)?
    private var bridge: BridgeChannel?
    private var isClosed = false

    func open(connection: Connection, keychain: KeychainStore) async throws -> BridgeChannel {
        guard let config = connection.sshConfig else { throw SSHError.missingCredentials }
        let credentials = try await keychain.sshCredentials(for: connection.id, authMethod: config.authMethod)
        let method = try SSHAuthenticationFactory.make(config: config, secret: credentials.secret, passphrase: credentials.passphrase)
        let validator = TOFUHostKeyValidator(connectionID: connection.id, keychain: keychain)
        let loop = MultiThreadedEventLoopGroup.singleton.next()
        let authenticated = loop.makePromise(of: Void.self)
        let bootstrap = ClientBootstrap(group: loop)
            .connectTimeout(.seconds(20))
            .channelInitializer { channel in
                guard self.install(channel) else {
                    return channel.eventLoop.makeFailedFuture(CancellationError())
                }
                let ssh = NIOSSHHandler(
                    role: .client(SSHClientConfiguration(userAuthDelegate: method, serverAuthDelegate: validator)),
                    allocator: channel.allocator,
                    inboundChildChannelInitializer: nil
                )
                do {
                    try channel.pipeline.syncOperations.addHandlers(ssh, SSHBridgeAuthenticationHandler(authenticated: authenticated))
                    return channel.eventLoop.makeSucceededVoidFuture()
                } catch {
                    authenticated.fail(error)
                    return channel.eventLoop.makeFailedFuture(error)
                }
            }
        do {
            let parent = try await bootstrap.connect(host: connection.host, port: connection.port).get()
            try await authenticated.futureResult.get()
            try Task.checkCancellation()
            let channel = try await openChannel(on: parent)
            guard let bridge = lock.withLock({ self.bridge }) else { throw CancellationError() }
            try await channel.triggerUserOutboundEvent(SSHChannelRequestEvent.ExecRequest(command: bridgeCommand(), wantReply: true)).get()
            try Task.checkCancellation()
            return bridge
        } catch {
            authenticated.fail(error)
            close()
            if error is CancellationError { throw error }
            Log.ssh.error("SSH bridge setup failed: \(String(describing: error), privacy: .private)")
            throw SSHError.connectionFailure(error)
        }
    }

    func close() {
        let resources = lock.withLock {
            isClosed = true
            let resources = (parent, bridge)
            parent = nil
            bridge = nil
            return resources
        }
        resources.1?.finish(exitStatus: nil)
        resources.0?.close(promise: nil)
    }

    private func install(_ channel: any Channel) -> Bool {
        lock.withLock {
            guard !isClosed else { return false }
            parent = channel
            return true
        }
    }

    private func openChannel(on parent: any Channel) async throws -> any Channel {
        try await parent.eventLoop.flatSubmit {
            let promise = parent.eventLoop.makePromise(of: (any Channel).self)
            do {
                let ssh = try parent.pipeline.syncOperations.handler(type: NIOSSHHandler.self)
                ssh.createChannel(promise) { channel, _ in
                    do {
                        let bridge = try BridgeChannel(writer: SSHBridgeWriter(channel: channel, session: self))
                        let accepted = self.lock.withLock {
                            guard !self.isClosed else { return false }
                            self.bridge = bridge
                            return true
                        }
                        guard accepted else {
                            bridge.finish(exitStatus: nil)
                            return channel.eventLoop.makeFailedFuture(CancellationError())
                        }
                        let handler = SSHBridgeOutputHandler(bridge: bridge, session: self)
                        return channel.setOption(ChannelOptions.autoRead, value: false).flatMap {
                            channel.setOption(ChannelOptions.allowRemoteHalfClosure, value: true)
                        }.flatMap {
                            channel.pipeline.addHandler(handler)
                        }
                    } catch {
                        return channel.eventLoop.makeFailedFuture(error)
                    }
                }
            } catch {
                promise.fail(error)
            }
            let timeout = parent.eventLoop.scheduleTask(in: .seconds(15)) {
                promise.fail(SSHError.unreachable)
                parent.close(promise: nil)
            }
            promise.futureResult.whenComplete { _ in timeout.cancel() }
            return promise.futureResult
        }.get()
    }
}

nonisolated private final class SSHBridgeWriter: ChannelWriter, @unchecked Sendable {
    private let channel: any Channel
    private weak var session: SSHBridgeSession?

    init(channel: any Channel, session: SSHBridgeSession) {
        self.channel = channel
        self.session = session
    }

    func write(bytes: Data) throws {
        do {
            try channel.writeAndFlush(SSHChannelData(type: .channel, data: .byteBuffer(ByteBuffer(bytes: bytes)))).wait()
        } catch {
            throw MobileError.Disconnected
        }
    }

    func close() throws {
        session?.close()
    }
}
