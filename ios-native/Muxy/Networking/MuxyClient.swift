import Foundation
import OSLog

actor MuxyClient {
    private let transport: Transport
    private let requestTimeout: Duration
    private let idProvider: @Sendable () -> String

    private var pending: [String: CheckedContinuation<RawTagged, Error>] = [:]
    private var readLoop: Task<Void, Never>?
    private var eventContinuation: AsyncStream<EventEnvelope>.Continuation?
    private var isStopped = false

    nonisolated let events: AsyncStream<EventEnvelope>

    init(
        transport: Transport,
        requestTimeout: Duration = .seconds(30),
        idProvider: @escaping @Sendable () -> String = { UUID().uuidString }
    ) {
        self.transport = transport
        self.requestTimeout = requestTimeout
        self.idProvider = idProvider

        var continuation: AsyncStream<EventEnvelope>.Continuation!
        events = AsyncStream { continuation = $0 }
        eventContinuation = continuation
    }

    func connect() async throws {
        try await transport.connect()
    }

    func start() {
        guard readLoop == nil else { return }
        readLoop = Task { [weak self] in
            await self?.runReadLoop()
        }
    }

    func request<P: Codable & Sendable>(_ method: Method, params: P?) async throws -> RawTagged {
        if let params {
            return try await send(method: method, params: TaggedPayload(type: method.rawValue, value: params))
        }
        return try await send(method: method, params: Optional<TaggedPayload<EmptyParams>>.none)
    }

    func notify<P: Codable & Sendable>(_ method: Method, params: P?) async throws {
        if let params {
            try await sendNotification(method: method, params: TaggedPayload(type: method.rawValue, value: params))
            return
        }
        try await sendNotification(method: method, params: Optional<TaggedPayload<EmptyParams>>.none)
    }

    func stop() async {
        guard !isStopped else { return }
        isStopped = true
        readLoop?.cancel()
        readLoop = nil
        eventContinuation?.finish()
        await transport.close()
        failPending(with: TransportError.closed)
    }

    private func send<P: Codable & Sendable>(method: Method, params: TaggedPayload<P>?) async throws -> RawTagged {
        let id = idProvider()
        let text = try encodeFrame(id: id, method: method, params: params)

        return try await withCheckedThrowingContinuation { continuation in
            pending[id] = continuation
            Task {
                await dispatch(id: id, text: text)
            }
        }
    }

    private func sendNotification<P: Codable & Sendable>(method: Method, params: TaggedPayload<P>?) async throws {
        let text = try encodeFrame(id: idProvider(), method: method, params: params)
        try await transport.send(text)
    }

    private func encodeFrame<P: Codable & Sendable>(id: String, method: Method, params: TaggedPayload<P>?) throws -> String {
        let envelope = RequestEnvelope(id: id, method: method.rawValue, params: params)
        let data = try JSONEncoder().encode(envelope)
        guard let text = String(data: data, encoding: .utf8) else { throw TransportError.unsupportedFrame }
        return text
    }

    private func dispatch(id: String, text: String) async {
        do {
            try await transport.send(text)
        } catch {
            resume(id: id, with: .failure(error))
            return
        }
        await scheduleTimeout(for: id)
    }

    private func scheduleTimeout(for id: String) async {
        let timeout = requestTimeout
        Task { [weak self] in
            try? await Task.sleep(for: timeout)
            await self?.timeout(id: id)
        }
    }

    private func timeout(id: String) {
        guard pending[id] != nil else { return }
        resume(id: id, with: .failure(TransportError.closed))
    }

    private func runReadLoop() async {
        while !Task.isCancelled {
            let text: String
            do {
                text = try await transport.receive()
            } catch {
                handleReadFailure(error)
                return
            }
            handle(text: text)
        }
    }

    private func handle(text: String) {
        guard let data = text.data(using: .utf8) else { return }

        let frame: IncomingFrame
        do {
            frame = try IncomingFrame(json: data)
        } catch {
            Log.client.error("Failed to decode frame: \(error.localizedDescription, privacy: .public)")
            return
        }

        switch frame {
        case let .response(response):
            handle(response: response)
        case let .event(event):
            eventContinuation?.yield(event)
        }
    }

    private func handle(response: ResponseEnvelope) {
        if let error = response.error {
            resume(id: response.id, with: .failure(ProtocolError(error)))
            return
        }
        guard let result = response.result else {
            resume(id: response.id, with: .failure(TransportError.unsupportedFrame))
            return
        }
        resume(id: response.id, with: .success(result))
    }

    private func handleReadFailure(_ error: Error) {
        Log.client.error("Read loop ended: \(error.localizedDescription, privacy: .public)")
        eventContinuation?.finish()
        failPending(with: error)
    }

    private func resume(id: String, with result: Result<RawTagged, Error>) {
        guard let continuation = pending.removeValue(forKey: id) else { return }
        continuation.resume(with: result)
    }

    private func failPending(with error: Error) {
        let continuations = pending
        pending.removeAll()
        for continuation in continuations.values {
            continuation.resume(throwing: error)
        }
    }
}

nonisolated private struct EmptyParams: Codable, Sendable {}
