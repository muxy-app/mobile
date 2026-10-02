import Foundation
import MuxyMobile

nonisolated final class ConnectionEventRelay: ConnectionListener, Sendable {
    private let handler: @Sendable (ConnectionEvent) -> Void

    init(handler: @escaping @Sendable (ConnectionEvent) -> Void) {
        self.handler = handler
    }

    func onEvent(event: ConnectionEvent) {
        handler(event)
    }
}
