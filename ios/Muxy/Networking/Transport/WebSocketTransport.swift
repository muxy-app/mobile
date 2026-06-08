import Foundation
import OSLog

actor WebSocketTransport: Transport {
    private let url: URL
    private let session: URLSession
    private var task: URLSessionWebSocketTask?

    init(url: URL, session: URLSession = URLSession(configuration: .ephemeral)) {
        self.url = url
        self.session = session
    }

    func connect() async throws {
        let task = session.webSocketTask(with: url)
        self.task = task
        task.resume()
        Log.transport.debug("WebSocket connecting to \(self.url.absoluteString, privacy: .public)")
    }

    func send(_ text: String) async throws {
        guard let task else { throw TransportError.notConnected }
        Log.transport.debug("WebSocket send: \(text, privacy: .private)")
        try await task.send(.string(text))
    }

    func receive() async throws -> String {
        guard let task else { throw TransportError.notConnected }

        while true {
            let message: URLSessionWebSocketTask.Message
            do {
                message = try await task.receive()
            } catch {
                Log.transport.error("WebSocket receive failed: \(error.localizedDescription, privacy: .public)")
                throw TransportError.closed
            }

            switch message {
            case let .string(text):
                Log.transport.debug("WebSocket recv: \(text, privacy: .private)")
                return text
            case let .data(data):
                guard let text = String(data: data, encoding: .utf8) else { continue }
                return text
            @unknown default:
                continue
            }
        }
    }

    func close() async {
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        Log.transport.debug("WebSocket closed")
    }
}
