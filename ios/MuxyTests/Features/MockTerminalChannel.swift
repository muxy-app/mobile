import Foundation
@testable import Muxy

actor MockTerminalChannel: TerminalChannel {
    struct Call {
        let method: Muxy.Method
        let params: Any?
    }

    private(set) var requests: [Call] = []
    private(set) var notifications: [Call] = []
    var currentTheme: DeviceThemeEvent?
    var currentClientID: UUID?

    private var continuation: AsyncStream<EventEnvelope>.Continuation?
    private var bufferedEvents: [EventEnvelope] = []
    private var requestErrors: [Muxy.Method: Error] = [:]
    private var requestWaiters: [Muxy.Method: [CheckedContinuation<Void, Never>]] = [:]
    private var notifyWaiters: [Muxy.Method: [CheckedContinuation<Void, Never>]] = [:]

    func request<P: Codable & Sendable>(_ method: Muxy.Method, params: P?) async throws -> RawTagged {
        requests.append(Call(method: method, params: params))
        resume(&requestWaiters, method)
        if let error = requestErrors[method] {
            throw error
        }
        return MockTerminalChannel.okResult
    }

    private static let okResult: RawTagged = {
        let data = Data(#"{ "type": "ok", "value": null }"#.utf8)
        return try! JSONDecoder().decode(RawTagged.self, from: data)
    }()

    func notify<P: Codable & Sendable>(_ method: Muxy.Method, params: P?) async throws {
        notifications.append(Call(method: method, params: params))
        resume(&notifyWaiters, method)
    }

    func events() async -> AsyncStream<EventEnvelope> {
        AsyncStream { continuation in
            self.continuation = continuation
            for event in bufferedEvents {
                continuation.yield(event)
            }
            bufferedEvents.removeAll()
        }
    }

    func setClientID(_ id: UUID?) {
        currentClientID = id
    }

    func setRequestError(_ error: Error, for method: Muxy.Method) {
        requestErrors[method] = error
    }

    func emitOwnership(paneID: UUID, owner: PaneOwner) {
        let value = PaneOwnershipEvent(paneID: paneID, owner: owner)
        emit(eventName: EventName.paneOwnershipChanged, type: EventType.paneOwnership, value: value)
    }

    func emitOutput(paneID: UUID, bytes: [UInt8]) {
        let value = TerminalBytesEvent(paneID: paneID, bytes: Data(bytes))
        emit(eventName: EventName.terminalOutput, type: EventType.terminalOutput, value: value)
    }

    func waitForRequest(_ method: Muxy.Method) async throws {
        if requests.contains(where: { $0.method == method }) { return }
        await withCheckedContinuation { continuation in
            requestWaiters[method, default: []].append(continuation)
        }
    }

    func waitForNotify(_ method: Muxy.Method) async throws {
        if notifications.contains(where: { $0.method == method }) { return }
        await withCheckedContinuation { continuation in
            notifyWaiters[method, default: []].append(continuation)
        }
    }

    private func emit(eventName: String, type: String, value: some Codable & Sendable) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        let rawData = try? JSONSerialization.jsonObject(with: data)
        let wrapped = try? JSONSerialization.data(withJSONObject: ["type": type, "value": rawData ?? [:]])
        guard let wrapped, let tagged = try? JSONDecoder().decode(RawTagged.self, from: wrapped) else { return }
        let envelope = EventEnvelope(event: eventName, data: tagged)
        guard let continuation else {
            bufferedEvents.append(envelope)
            return
        }
        continuation.yield(envelope)
    }

    private func resume(_ waiters: inout [Muxy.Method: [CheckedContinuation<Void, Never>]], _ method: Muxy.Method) {
        guard let pending = waiters[method] else { return }
        waiters[method] = nil
        for waiter in pending {
            waiter.resume()
        }
    }
}
