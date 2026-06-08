import Foundation
import OSLog

actor ConnectionManager {
    typealias TransportFactory = @Sendable (URL) -> Transport

    private let makeTransport: TransportFactory
    private let pairingService: PairingService

    private var client: MuxyClient?
    private var connectedDeviceID: Device.ID?
    private var theme: DeviceThemeEvent?
    private var clientID: UUID?
    private var state: ConnectionState = .idle {
        didSet { broadcast(state) }
    }

    private var stateContinuations: [UUID: AsyncStream<ConnectionState>.Continuation] = [:]
    private var eventContinuations: [UUID: AsyncStream<EventEnvelope>.Continuation] = [:]
    private var eventPump: Task<Void, Never>?

    init(makeTransport: @escaping TransportFactory, pairingService: PairingService = LivePairingService()) {
        self.makeTransport = makeTransport
        self.pairingService = pairingService
    }

    var currentState: ConnectionState {
        state
    }

    var currentTheme: DeviceThemeEvent? {
        theme
    }

    var currentClientID: UUID? {
        clientID
    }

    func stateUpdates() -> AsyncStream<ConnectionState> {
        AsyncStream { continuation in
            let id = UUID()
            stateContinuations[id] = continuation
            continuation.yield(state)
            continuation.onTermination = { [weak self] _ in
                Task { await self?.removeContinuation(id) }
            }
        }
    }

    func events() -> AsyncStream<EventEnvelope> {
        AsyncStream { continuation in
            let id = UUID()
            eventContinuations[id] = continuation
            continuation.onTermination = { [weak self] _ in
                Task { await self?.removeEventContinuation(id) }
            }
        }
    }

    func request<P: Codable & Sendable>(_ method: Method, params: P?) async throws -> RawTagged {
        guard let client else { throw ConnectionError.notConnected }
        return try await client.request(method, params: params)
    }

    func request(_ method: Method) async throws -> RawTagged {
        try await request(method, params: Optional<EmptyRequestParams>.none)
    }

    func notify<P: Codable & Sendable>(_ method: Method, params: P?) async throws {
        guard let client else { throw ConnectionError.notConnected }
        try await client.notify(method, params: params)
    }

    func beginPairing(
        device: Device,
        token: String,
        onStatus: @escaping @Sendable (PairingStatus) -> Void
    ) async -> PairingStatus {
        await teardownClient()
        state = .connecting
        onStatus(.connecting)

        guard let url = device.endpoint.webSocketURL else {
            onStatus(.failed(.connectionFailed))
            state = .failed(.invalidEndpoint)
            return .failed(.connectionFailed)
        }

        let client = makeClient(url: url)
        do {
            try await client.connect()
        } catch {
            onStatus(.failed(.connectionFailed))
            state = .failed(.connectionFailed)
            return .failed(.connectionFailed)
        }
        await client.start()

        let params = AuthParams(deviceID: device.id.uuidString, deviceName: device.name, token: token)
        let status = await pairingService.pair(using: client, params: params, onStatus: onStatus)

        guard case .paired = status else {
            await teardownClient()
            state = .failed(.authenticationFailed)
            return status
        }

        connectedDeviceID = device.id
        state = .connected
        return status
    }

    func ensureConnected(device: Device, token: String) async {
        if connectedDeviceID == device.id, case .connected = state { return }
        await connect(to: device, token: token)
    }

    func connect(to device: Device, token: String) async {
        await teardownClient()
        state = .connecting

        guard let url = device.endpoint.webSocketURL else {
            state = .failed(.invalidEndpoint)
            return
        }

        let client = makeClient(url: url)
        do {
            try await client.connect()
        } catch {
            state = .failed(.connectionFailed)
            return
        }
        await client.start()
        state = .authenticating

        let params = AuthParams(deviceID: device.id.uuidString, deviceName: device.name, token: token)
        do {
            let result = try await client.request(.authenticateDevice, params: params)
            captureAuthResult(result)
            connectedDeviceID = device.id
            state = .connected
        } catch {
            await teardownClient()
            state = .failed(.authenticationFailed)
        }
    }

    private func captureAuthResult(_ result: RawTagged) {
        guard result.type == ResultType.pairing else { return }
        guard let pairing = try? result.decode(PairingResult.self) else { return }
        clientID = UUID(uuidString: pairing.clientID)
        theme = pairing.deviceTheme
    }

    func disconnect() async {
        await teardownClient()
        connectedDeviceID = nil
        theme = nil
        clientID = nil
        state = .disconnected
    }

    private func makeClient(url: URL) -> MuxyClient {
        let transport = makeTransport(url)
        let client = MuxyClient(transport: transport)
        self.client = client
        startEventPump(for: client)
        return client
    }

    private func teardownClient() async {
        eventPump?.cancel()
        eventPump = nil
        guard let client else { return }
        await client.stop()
        self.client = nil
    }

    private func startEventPump(for client: MuxyClient) {
        eventPump?.cancel()
        eventPump = Task { [weak self] in
            for await event in client.events {
                await self?.broadcastEvent(event)
            }
        }
    }

    private func broadcast(_ state: ConnectionState) {
        for continuation in stateContinuations.values {
            continuation.yield(state)
        }
    }

    private func broadcastEvent(_ event: EventEnvelope) {
        cacheThemeIfNeeded(event)
        for continuation in eventContinuations.values {
            continuation.yield(event)
        }
    }

    private func cacheThemeIfNeeded(_ event: EventEnvelope) {
        guard event.event == EventName.themeChanged else { return }
        guard let data = event.data, data.type == EventType.deviceTheme else { return }
        guard let deviceTheme = try? data.decode(DeviceThemeEvent.self) else { return }
        theme = deviceTheme
    }

    private func removeContinuation(_ id: UUID) {
        stateContinuations[id] = nil
    }

    private func removeEventContinuation(_ id: UUID) {
        eventContinuations[id] = nil
    }
}
