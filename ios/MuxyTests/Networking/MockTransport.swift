import Foundation
@testable import Muxy

actor MockTransport: Transport {
    private(set) var sentFrames: [String] = []
    private(set) var didConnect = false
    private(set) var didClose = false

    private var inbound: [String] = []
    private var waiters: [CheckedContinuation<String, Error>] = []
    private var closed = false

    private var onSend: (@Sendable (String) -> [String])?

    init(autoReply: (@Sendable (String) -> [String])? = nil) {
        onSend = autoReply
    }

    func connect() async throws {
        didConnect = true
    }

    func send(_ text: String) async throws {
        sentFrames.append(text)
        guard let replies = onSend?(text) else { return }
        for reply in replies {
            enqueue(reply)
        }
    }

    func receive() async throws -> String {
        if !inbound.isEmpty {
            return inbound.removeFirst()
        }
        if closed {
            throw TransportError.closed
        }
        return try await withCheckedThrowingContinuation { waiters.append($0) }
    }

    func close() async {
        didClose = true
        closed = true
        let pending = waiters
        waiters.removeAll()
        for waiter in pending {
            waiter.resume(throwing: TransportError.closed)
        }
    }

    func enqueue(_ text: String) {
        if waiters.isEmpty {
            inbound.append(text)
            return
        }
        let waiter = waiters.removeFirst()
        waiter.resume(returning: text)
    }

    func failReaders() async {
        closed = true
        let pending = waiters
        waiters.removeAll()
        for waiter in pending {
            waiter.resume(throwing: TransportError.closed)
        }
    }
}
