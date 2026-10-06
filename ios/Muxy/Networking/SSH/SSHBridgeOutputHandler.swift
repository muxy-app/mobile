import Foundation
import MuxyMobile
import NIOCore
import NIOSSH
import OSLog

nonisolated final class SSHBridgeOutputHandler: ChannelInboundHandler, @unchecked Sendable {
    typealias InboundIn = SSHChannelData

    private let bridge: BridgeChannel
    private let session: SSHBridgeSession
    private let output = DispatchQueue(label: "app.muxy.ssh.bridge.output", qos: .userInitiated)
    private var pendingOutput = 0
    private var readCompleted = false
    private var ended = false
    private var exitStatus: Int32?

    init(bridge: BridgeChannel, session: SSHBridgeSession) {
        self.bridge = bridge
        self.session = session
    }

    func channelActive(context: ChannelHandlerContext) {
        context.read()
        context.fireChannelActive()
    }

    func channelRead(context: ChannelHandlerContext, data: NIOAny) {
        let message = unwrapInboundIn(data)
        guard case let .byteBuffer(bytes) = message.data else { return }
        let isError = message.type == .stdErr
        guard isError || message.type == .channel else { return }
        let data = Data(bytes.readableBytesView)
        let channel = context.channel
        pendingOutput += 1
        output.async { [self] in
            if isError {
                bridge.receiveError(bytes: data)
            } else {
                bridge.receive(bytes: data)
            }
            channel.eventLoop.execute { [self] in
                pendingOutput -= 1
                requestNextRead(on: channel)
            }
        }
    }

    func channelReadComplete(context: ChannelHandlerContext) {
        readCompleted = true
        requestNextRead(on: context.channel)
    }

    func userInboundEventTriggered(context: ChannelHandlerContext, event: Any) {
        if let status = event as? SSHChannelRequestEvent.ExitStatus {
            exitStatus = Int32(exactly: status.exitStatus)
            return
        }
        if event is ChannelFailureEvent {
            context.close(promise: nil)
            return
        }
        context.fireUserInboundEventTriggered(event)
    }

    func errorCaught(context: ChannelHandlerContext, error: any Error) {
        Log.ssh.debug("SSH bridge channel failed: \(String(describing: error), privacy: .private)")
        context.close(promise: nil)
    }

    func channelInactive(context: ChannelHandlerContext) {
        finish()
        context.fireChannelInactive()
    }

    func handlerRemoved(context: ChannelHandlerContext) {
        finish()
    }

    private func requestNextRead(on channel: any Channel) {
        guard !ended, readCompleted, pendingOutput == 0 else { return }
        readCompleted = false
        channel.read()
    }

    private func finish() {
        guard !ended else { return }
        ended = true
        let status = exitStatus
        output.async { [bridge, session] in
            bridge.finish(exitStatus: status)
            session.close()
        }
    }
}
