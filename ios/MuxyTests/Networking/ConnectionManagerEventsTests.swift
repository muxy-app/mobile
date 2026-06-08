import Foundation
import Testing
@testable import Muxy

struct ConnectionManagerEventsTests {
    private static func authReply(_ frame: String) -> [String] {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any],
            let id = payload["id"] as? String
        else { return [] }
        return ["""
        { "type": "response", "payload": { "id": "\(id)", "result": { "type": "pairing",
          "value": { "clientID": "c", "deviceName": "iPhone" } } } }
        """]
    }

    private static func projectsEvent() -> String {
        """
        { "type": "event", "payload": { "event": "projectsChanged",
          "data": { "type": "projects", "value": { "projects": [] } } } }
        """
    }

    private func device() -> Device {
        Device(
            id: UUID(),
            name: "Studio",
            host: "studio.local",
            port: 4865,
            pairingState: .paired,
            serviceName: nil,
            discoverySource: .manual
        )
    }

    @Test func eventsAreForwardedToSubscriber() async throws {
        let holder = TransportHolder()
        let manager = ConnectionManager(makeTransport: { _ in holder.make(autoReply: ConnectionManagerEventsTests.authReply) })

        await manager.connect(to: device(), token: "t")
        let stream = await manager.events()
        var iterator = stream.makeAsyncIterator()

        await holder.latest()?.enqueue(ConnectionManagerEventsTests.projectsEvent())
        let event = await iterator.next()

        #expect(event?.event == EventName.projectsChanged)
    }

    @Test func eventsFanOutToMultipleSubscribers() async throws {
        let holder = TransportHolder()
        let manager = ConnectionManager(makeTransport: { _ in holder.make(autoReply: ConnectionManagerEventsTests.authReply) })

        await manager.connect(to: device(), token: "t")
        var first = await manager.events().makeAsyncIterator()
        var second = await manager.events().makeAsyncIterator()

        await holder.latest()?.enqueue(ConnectionManagerEventsTests.projectsEvent())

        let a = await first.next()
        let b = await second.next()

        #expect(a?.event == EventName.projectsChanged)
        #expect(b?.event == EventName.projectsChanged)
    }

    @Test func subscriberSurvivesReconnect() async throws {
        let holder = TransportHolder()
        let manager = ConnectionManager(makeTransport: { _ in holder.make(autoReply: ConnectionManagerEventsTests.authReply) })

        await manager.connect(to: device(), token: "t")
        let stream = await manager.events()
        var iterator = stream.makeAsyncIterator()

        await manager.disconnect()
        await manager.connect(to: device(), token: "t")

        await holder.latest()?.enqueue(ConnectionManagerEventsTests.projectsEvent())
        let event = await iterator.next()

        #expect(event?.event == EventName.projectsChanged)
    }
}

private final class TransportHolder: @unchecked Sendable {
    private let lock = NSLock()
    private var transports: [MockTransport] = []

    func make(autoReply: @escaping @Sendable (String) -> [String]) -> MockTransport {
        let transport = MockTransport(autoReply: autoReply)
        lock.lock()
        transports.append(transport)
        lock.unlock()
        return transport
    }

    func latest() -> MockTransport? {
        lock.lock()
        defer { lock.unlock() }
        return transports.last
    }
}
