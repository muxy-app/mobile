import Foundation
import Testing
@testable import Muxy

struct ConnectionManagerTests {
    private func requestID(from frame: String) -> String {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any],
            let id = payload["id"] as? String
        else { return "" }
        return id
    }

    private func pairing(id: String) -> String {
        """
        { "type": "response", "payload": { "id": "\(id)", "result": { "type": "pairing",
          "value": { "clientID": "c", "deviceName": "iPhone" } } } }
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

    private func successFactory() -> (factory: ConnectionManager.TransportFactory, count: @Sendable () -> Int) {
        let counter = Counter()
        let factory: ConnectionManager.TransportFactory = { _ in
            counter.increment()
            return MockTransport { frame in
                let id = ConnectionManagerTests.id(from: frame)
                return [ConnectionManagerTests.pairingFrame(id: id)]
            }
        }
        return (factory, { counter.value() })
    }

    private static func id(from frame: String) -> String {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any],
            let id = payload["id"] as? String
        else { return "" }
        return id
    }

    private static func pairingFrame(id: String) -> String {
        """
        { "type": "response", "payload": { "id": "\(id)", "result": { "type": "pairing",
          "value": { "clientID": "c", "deviceName": "iPhone" } } } }
        """
    }

    @Test func connectReachesConnectedState() async {
        let (factory, count) = successFactory()
        let manager = ConnectionManager(makeTransport: factory)
        let device = device()

        await manager.connect(to: device, token: "t")
        let state = await manager.currentState

        #expect(state == .connected)
        #expect(count() == 1)
    }

    @Test func ensureConnectedIsNoOpWhenAlreadyConnected() async {
        let (factory, count) = successFactory()
        let manager = ConnectionManager(makeTransport: factory)
        let device = device()

        await manager.connect(to: device, token: "t")
        await manager.ensureConnected(device: device, token: "t")
        let state = await manager.currentState

        #expect(state == .connected)
        #expect(count() == 1)
    }

    @Test func ensureConnectedConnectsWhenNotConnected() async {
        let (factory, count) = successFactory()
        let manager = ConnectionManager(makeTransport: factory)

        await manager.ensureConnected(device: device(), token: "t")
        let state = await manager.currentState

        #expect(state == .connected)
        #expect(count() == 1)
    }

    @Test func ensureConnectedReconnectsForDifferentDevice() async {
        let (factory, count) = successFactory()
        let manager = ConnectionManager(makeTransport: factory)

        await manager.connect(to: device(), token: "t")
        await manager.ensureConnected(device: device(), token: "t")
        let state = await manager.currentState

        #expect(state == .connected)
        #expect(count() == 2)
    }

    @Test func disconnectMovesToDisconnected() async {
        let (factory, _) = successFactory()
        let manager = ConnectionManager(makeTransport: factory)
        let device = device()

        await manager.connect(to: device, token: "t")
        await manager.disconnect()
        let state = await manager.currentState

        #expect(state == .disconnected)
    }

    @Test func beginPairingReusesSocketAndConnects() async {
        let (factory, count) = successFactory()
        let manager = ConnectionManager(makeTransport: factory)
        let device = device()

        let status = await manager.beginPairing(device: device, token: "t") { _ in }
        guard case .paired = status else { Issue.record("Expected paired"); return }

        await manager.ensureConnected(device: device, token: "t")
        let state = await manager.currentState

        #expect(state == .connected)
        #expect(count() == 1)
    }

    @Test func failedEndpointReportsFailure() async {
        let (factory, _) = successFactory()
        let manager = ConnectionManager(makeTransport: factory)
        let device = Device(
            id: UUID(),
            name: "Bad",
            host: "",
            port: 4865,
            pairingState: .notPaired,
            serviceName: nil,
            discoverySource: .manual
        )

        await manager.connect(to: device, token: "t")
        let state = await manager.currentState
        #expect(state == .failed(.invalidEndpoint))
    }
}

private final class Counter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0

    func increment() {
        lock.lock()
        count += 1
        lock.unlock()
    }

    func value() -> Int {
        lock.lock()
        defer { lock.unlock() }
        return count
    }
}
