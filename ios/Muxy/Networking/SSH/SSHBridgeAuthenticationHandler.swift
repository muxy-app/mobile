import NIOCore
import NIOSSH

nonisolated final class SSHBridgeAuthenticationHandler: ChannelInboundHandler, @unchecked Sendable {
    typealias InboundIn = Any

    private let authenticated: EventLoopPromise<Void>
    private var timeout: Scheduled<Void>?
    private var hasCompleted = false

    init(authenticated: EventLoopPromise<Void>) {
        self.authenticated = authenticated
    }

    func handlerAdded(context: ChannelHandlerContext) {
        let channel = context.channel
        timeout = context.eventLoop.scheduleTask(in: .seconds(20)) {
            self.complete(.failure(SSHError.unreachable))
            channel.close(promise: nil)
        }
    }

    func userInboundEventTriggered(context: ChannelHandlerContext, event: Any) {
        guard event is UserAuthSuccessEvent else {
            context.fireUserInboundEventTriggered(event)
            return
        }
        complete(.success(()))
    }

    func errorCaught(context: ChannelHandlerContext, error: any Error) {
        complete(.failure(error))
        context.close(promise: nil)
    }

    func channelInactive(context: ChannelHandlerContext) {
        complete(.failure(SSHError.unreachable))
        context.fireChannelInactive()
    }

    func handlerRemoved(context: ChannelHandlerContext) {
        complete(.failure(SSHError.unreachable))
    }

    private func complete(_ result: Result<Void, Error>) {
        guard !hasCompleted else { return }
        hasCompleted = true
        timeout?.cancel()
        timeout = nil
        authenticated.completeWith(result)
    }
}
