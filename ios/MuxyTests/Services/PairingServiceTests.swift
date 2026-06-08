import Foundation
import Testing
@testable import Muxy

struct PairingServiceTests {
    private func method(from frame: String) -> String? {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any]
        else { return nil }
        return payload["method"] as? String
    }

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

    private func error(id: String, code: Int) -> String {
        """
        { "type": "response", "payload": { "id": "\(id)", "error": { "code": \(code), "message": "e" } } }
        """
    }

    private func makeClient(reply: @escaping @Sendable (String) -> [String]) async -> MuxyClient {
        let transport = MockTransport(autoReply: reply)
        let client = MuxyClient(transport: transport, requestTimeout: .seconds(2))
        await client.start()
        return client
    }

    private var params: AuthParams {
        AuthParams(deviceID: "d", deviceName: "iPhone", token: "t")
    }

    private func collectStatuses() -> (callback: @Sendable (PairingStatus) -> Void, read: @Sendable () -> [PairingStatus]) {
        let box = StatusBox()
        return ({ box.append($0) }, { box.snapshot() })
    }

    @Test func authenticateSuccessReturnsPaired() async {
        let client = await makeClient { [self] frame in [pairing(id: requestID(from: frame))] }
        let (callback, read) = collectStatuses()

        let status = await LivePairingService().pair(using: client, params: params, onStatus: callback)

        guard case .paired = status else { Issue.record("Expected paired"); return }
        #expect(read().contains(.authenticating))
        #expect(!read().contains(.awaitingApproval))
        await client.stop()
    }

    @Test func unknownDeviceFallsBackToPairing() async {
        let client = await makeClient { [self] frame in
            let id = requestID(from: frame)
            guard method(from: frame) == "authenticateDevice" else { return [pairing(id: id)] }
            return [error(id: id, code: 401)]
        }
        let (callback, read) = collectStatuses()

        let status = await LivePairingService().pair(using: client, params: params, onStatus: callback)

        guard case .paired = status else { Issue.record("Expected paired"); return }
        #expect(read().contains(.awaitingApproval))
        await client.stop()
    }

    @Test func pairingDeniedReturnsApprovalDenied() async {
        let client = await makeClient { [self] frame in
            let id = requestID(from: frame)
            let code = method(from: frame) == "authenticateDevice" ? 401 : 403
            return [error(id: id, code: code)]
        }
        let (callback, _) = collectStatuses()

        let status = await LivePairingService().pair(using: client, params: params, onStatus: callback)
        #expect(status == .failed(.approvalDenied))
        await client.stop()
    }

    @Test func pairingTimeoutReturnsApprovalTimedOut() async {
        let client = await makeClient { [self] frame in
            let id = requestID(from: frame)
            let code = method(from: frame) == "authenticateDevice" ? 401 : 408
            return [error(id: id, code: code)]
        }
        let (callback, _) = collectStatuses()

        let status = await LivePairingService().pair(using: client, params: params, onStatus: callback)
        #expect(status == .failed(.approvalTimedOut))
        await client.stop()
    }

    @Test func wrongTokenReturnsWrongToken() async {
        let client = await makeClient { [self] frame in [error(id: requestID(from: frame), code: 403)] }
        let (callback, read) = collectStatuses()

        let status = await LivePairingService().pair(using: client, params: params, onStatus: callback)
        #expect(status == .failed(.wrongToken))
        #expect(!read().contains(.awaitingApproval))
        await client.stop()
    }
}

private final class StatusBox: @unchecked Sendable {
    private let lock = NSLock()
    private var statuses: [PairingStatus] = []

    func append(_ status: PairingStatus) {
        lock.lock()
        statuses.append(status)
        lock.unlock()
    }

    func snapshot() -> [PairingStatus] {
        lock.lock()
        defer { lock.unlock() }
        return statuses
    }
}
