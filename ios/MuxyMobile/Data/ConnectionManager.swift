import Foundation
import os
import SwiftUI
import UIKit

private let logger = Logger(subsystem: "app.muxy", category: "Connection")

@MainActor
@Observable
final class ConnectionManager {
    typealias DeviceTheme = MuxyMobile.DeviceTheme
    typealias SavedDevice = MuxyMobile.SavedDevice
    typealias ConnectionIssue = MuxyMobile.ConnectionIssue

    enum State {
        case disconnected
        case connecting
        case awaitingApproval
        case connected
        case error(ConnectionIssue)
    }

    private struct PendingRequest {
        let method: MuxyMethod
        let continuation: CheckedContinuation<MuxyResponse, Never>
    }

    var state: State = .disconnected
    var projects: [ProjectDTO] = []
    var activeProjectID: UUID?
    var worktrees: [WorktreeDTO] = []
    var workspace: WorkspaceDTO?
    var notifications: [NotificationDTO] = []
    var projectLogos: [UUID: Data] = [:]
    var projectWorktrees: [UUID: [WorktreeDTO]] = [:]
    var deviceTheme: DeviceTheme?
    var paneOwners: [UUID: PaneOwnerDTO] = [:]
    private(set) var savedDevices: [SavedDevice] = []
    private(set) var myClientID: UUID?

    var deviceName: String {
        UIDevice.current.name
    }

    func paneOwner(for paneID: UUID) -> PaneOwnerDTO? {
        paneOwners[paneID]
    }

    func paneIsOwnedBySelf(_ paneID: UUID) -> Bool {
        guard let myClientID, let owner = paneOwners[paneID] else { return false }
        if case let .remote(id, _) = owner, id == myClientID { return true }
        return false
    }

    private var connection: URLSessionWebSocketTask?
    private var session: URLSession?
    private var pendingRequests: [String: PendingRequest] = [:]
    private var terminalByteHandlers: [UUID: (Data) -> Void] = [:]
    @ObservationIgnored private var demo: DemoBackend?
    @ObservationIgnored private var connectTask: Task<Void, Never>?
    @ObservationIgnored private var diagnostics = ConnectionDiagnostics()

    var isDemoMode: Bool {
        get { UserDefaults.standard.bool(forKey: Self.demoModeKey) }
        set { setDemoMode(newValue) }
    }

    func subscribeTerminalBytes(paneID: UUID, handler: @escaping (Data) -> Void) {
        terminalByteHandlers[paneID] = handler
    }

    func unsubscribeTerminalBytes(paneID: UUID) {
        terminalByteHandlers.removeValue(forKey: paneID)
    }

    func terminalByteHandler(for paneID: UUID) -> ((Data) -> Void)? {
        terminalByteHandlers[paneID]
    }

    private var lastHost: String?
    private var lastPort: UInt16?
    private var lastDeviceName: String?
    private var isBackgrounded = false
    private var isReconnecting = false

    var lastSavedHost: String? { savedDevices.first?.host }
    var lastSavedPort: UInt16? { savedDevices.first?.port }

    init() {
        if UserDefaults.standard.bool(forKey: Self.demoModeKey) {
            let backend = DemoBackend()
            backend.owner = self
            demo = backend
            savedDevices = backend.savedDevices
        } else {
            savedDevices = SavedDevicesStore.load()
        }
    }

    func setDemoMode(_ enabled: Bool) {
        guard enabled != UserDefaults.standard.bool(forKey: Self.demoModeKey) else { return }
        disconnect()
        UserDefaults.standard.set(enabled, forKey: Self.demoModeKey)
        if enabled {
            let backend = DemoBackend()
            backend.owner = self
            demo = backend
            savedDevices = backend.savedDevices
        } else {
            demo = nil
            savedDevices = SavedDevicesStore.load()
        }
        projects = []
        worktrees = []
        projectWorktrees = [:]
        workspace = nil
        notifications = []
        projectLogos = [:]
        paneOwners = [:]
        myClientID = nil
    }

    func connect(host: String, port: UInt16 = 4865, name: String = "Mac") {
        lastHost = host
        lastPort = port
        lastDeviceName = name
        diagnostics.clear()
        diagnostics.record("Connect requested for \(name) at \(host):\(port)")
        addDevice(name: name, host: host, port: port)
        state = .connecting
        activeProjectID = nil
        workspace = nil
        paneOwners = [:]
        deviceTheme = nil

        if let demo {
            myClientID = DemoBackend.myClientID
            deviceTheme = DemoBackend.theme
            projects = demo.projects
            for project in demo.projects {
                projectWorktrees[project.id] = demo.worktrees(for: project.id)
            }
            state = .connected
            return
        }

        openSocket(host: host, port: port)

        connectTask?.cancel()
        connectTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(500))
            if Task.isCancelled { return }
            guard let self else { return }
            guard await authenticateOrPair() else { return }
            if Task.isCancelled { return }
            await refreshProjects()
            if Task.isCancelled { return }
            switch state {
            case .connecting,
                 .awaitingApproval:
                state = .connected
            default:
                break
            }
        }
    }

    private func openSocket(host: String, port: UInt16) {
        connection?.cancel(with: .goingAway, reason: nil)
        connection = nil
        session = nil

        diagnostics.record("Opening WebSocket to \(host):\(port)")

        guard let url = URL(string: "ws://\(host):\(port)") else {
            fail(
                "Invalid device address",
                operation: "Opening WebSocket",
                notes: ["Could not construct a WebSocket URL from host '\(host)' and port \(port)."]
            )
            return
        }

        session = URLSession(configuration: .default)
        connection = session?.webSocketTask(with: url)
        connection?.resume()

        receiveLoop()
    }

    private func authenticateOrPair() async -> Bool {
        let credentials = DeviceCredentialsStore.load()
        let authParams = AuthenticateDeviceParams(
            deviceID: credentials.deviceID,
            deviceName: deviceName,
            token: credentials.token
        )
        guard let authResponse = await send(
            .authenticateDevice,
            params: .authenticateDevice(authParams),
            timeout: .seconds(10)
        )
        else {
            if case .error = state {
                return false
            }
            fail(
                "Could not reach device",
                operation: "Authenticating device",
                requestMethod: .authenticateDevice,
                notes: ["No authentication response was received."]
            )
            return false
        }

        if authResponse.error == nil {
            return handlePairingResult(
                authResponse.result,
                userMessage: "Authentication failed",
                operation: "Authenticating device",
                requestMethod: .authenticateDevice,
                requestID: authResponse.id
            )
        }
        if authResponse.error?.code != 401 {
            fail(
                "Authentication failed",
                operation: "Authenticating device",
                requestMethod: .authenticateDevice,
                response: authResponse
            )
            return false
        }

        state = .awaitingApproval
        let pairParams = PairDeviceParams(
            deviceID: credentials.deviceID,
            deviceName: deviceName,
            token: credentials.token
        )
        guard let pairResponse = await send(
            .pairDevice,
            params: .pairDevice(pairParams),
            timeout: .seconds(120)
        )
        else {
            if case .error = state {
                return false
            }
            fail(
                "Could not finish pairing",
                operation: "Pairing device",
                requestMethod: .pairDevice,
                notes: ["No pairing response was received."]
            )
            return false
        }

        if let error = pairResponse.error {
            fail(
                error.code == 403 ? "Approval denied on Mac" : "Could not finish pairing",
                operation: "Pairing device",
                requestMethod: .pairDevice,
                response: pairResponse
            )
            return false
        }
        return handlePairingResult(
            pairResponse.result,
            userMessage: "Could not finish pairing",
            operation: "Pairing device",
            requestMethod: .pairDevice,
            requestID: pairResponse.id
        )
    }

    private func handlePairingResult(
        _ result: MuxyResult?,
        userMessage: String,
        operation: String,
        requestMethod: MuxyMethod,
        requestID: String
    ) -> Bool {
        guard case let .pairing(info) = result else {
            fail(
                userMessage,
                operation: operation,
                requestMethod: requestMethod,
                requestID: requestID,
                notes: [
                    "Expected result: pairing",
                    "Actual result: \(ConnectionDiagnostics.resultSummary(result))",
                ]
            )
            return false
        }
        diagnostics.record("Authenticated as client \(info.clientID.uuidString)")
        myClientID = info.clientID
        if let fg = info.themeFg, let bg = info.themeBg {
            deviceTheme = DeviceTheme(fg: fg, bg: bg, palette: info.themePalette ?? [])
        }
        return true
    }

    func takeOverPane(paneID: UUID, cols: UInt32, rows: UInt32) async {
        let params = TakeOverPaneParams(paneID: paneID, cols: cols, rows: rows)
        _ = await send(.takeOverPane, params: .takeOverPane(params))
    }

    func releasePane(paneID: UUID) async {
        let params = ReleasePaneParams(paneID: paneID)
        _ = await send(.releasePane, params: .releasePane(params))
    }

    func disconnect() {
        diagnostics.record("Disconnected")
        state = .disconnected
        connectTask?.cancel()
        connectTask = nil
        connection?.cancel(with: .goingAway, reason: nil)
        connection = nil
        session = nil
        activeProjectID = nil
        workspace = nil
        deviceTheme = nil
        for (id, pending) in pendingRequests {
            pending.continuation.resume(returning: MuxyResponse(id: id, error: MuxyError(code: 499, message: "Cancelled")))
        }
        pendingRequests.removeAll()
    }

    func reconnect() {
        guard let host = lastHost, let port = lastPort else { return }
        connect(host: host, port: port, name: lastDeviceName ?? "Mac")
    }

    func handleBackground() {
        isBackgrounded = true
    }

    func handleForeground() {
        isBackgrounded = false
        guard lastHost != nil, lastPort != nil else { return }
        switch state {
        case .error:
            reconnect()
        case .connected:
            verifyConnectionOrReconnect()
        case .connecting,
             .awaitingApproval,
             .disconnected:
            break
        }
    }

    private func verifyConnectionOrReconnect() {
        guard let connection else {
            reconnectSilently()
            return
        }
        connection.sendPing { [weak self] error in
            guard error != nil else { return }
            Task { @MainActor in
                self?.reconnectSilently()
            }
        }
    }

    private func reconnectSilently() {
        guard let host = lastHost, let port = lastPort else { return }
        guard !isReconnecting else { return }
        isReconnecting = true

        paneOwners = [:]
        openSocket(host: host, port: port)

        Task {
            defer { isReconnecting = false }
            try? await Task.sleep(for: .milliseconds(500))
            guard await authenticateOrPair() else {
                if case .error = state {
                    return
                }
                fail("Connection lost", operation: "Reconnecting")
                return
            }
            await refreshProjects()
            if case .error = state {
                return
            }
            if let projectID = activeProjectID {
                await selectProject(projectID)
            }
        }
    }

    func refreshProjects() async {
        diagnostics.record("Refreshing project list")

        guard let response = await send(.listProjects) else {
            if case .error = state {
                return
            }
            fail(
                "Could not load projects",
                operation: "Loading project list",
                requestMethod: .listProjects,
                notes: ["No project list response was received."]
            )
            return
        }

        if response.error != nil {
            fail(
                "Could not load projects",
                operation: "Loading project list",
                requestMethod: .listProjects,
                response: response
            )
            return
        }

        guard case let .projects(list) = response.result else {
            fail(
                "Could not load projects",
                operation: "Loading project list",
                requestMethod: .listProjects,
                requestID: response.id,
                notes: [
                    "Expected result: projects",
                    "Actual result: \(ConnectionDiagnostics.resultSummary(response.result))",
                ]
            )
            return
        }

        projects = list
        for project in list {
            if project.logo != nil {
                await fetchLogo(for: project.id)
            }
            await refreshWorktrees(projectID: project.id)
        }
    }

    func fetchLogo(for projectID: UUID) async {
        guard projectLogos[projectID] == nil else { return }
        let params = GetProjectLogoParams(projectID: projectID)
        guard let response = await send(.getProjectLogo, params: .getProjectLogo(params)),
              case let .projectLogo(logo) = response.result,
              let data = Data(base64Encoded: logo.pngData)
        else { return }
        projectLogos[projectID] = data
    }

    func selectProject(_ projectID: UUID) async {
        diagnostics.record("Selecting project \(projectID.uuidString)")
        activeProjectID = projectID
        workspace = nil
        paneOwners = [:]
        let params = SelectProjectParams(projectID: projectID)
        guard let response = await send(.selectProject, params: .selectProject(params)) else {
            if case .error = state {
                return
            }
            fail(
                "Could not open project session",
                operation: "Selecting project",
                requestMethod: .selectProject,
                notes: ["Project ID: \(projectID.uuidString)"]
            )
            return
        }

        if response.error != nil {
            fail(
                "Could not open project session",
                operation: "Selecting project",
                requestMethod: .selectProject,
                response: response,
                notes: ["Project ID: \(projectID.uuidString)"]
            )
            return
        }

        guard case .ok? = response.result else {
            fail(
                "Could not open project session",
                operation: "Selecting project",
                requestMethod: .selectProject,
                requestID: response.id,
                notes: [
                    "Project ID: \(projectID.uuidString)",
                    "Expected result: ok",
                    "Actual result: \(ConnectionDiagnostics.resultSummary(response.result))",
                ]
            )
            return
        }

        await refreshWorkspace(projectID: projectID)
    }

    func refreshWorktrees(projectID: UUID) async {
        let params = ListWorktreesParams(projectID: projectID)
        guard let response = await send(.listWorktrees, params: .listWorktrees(params)) else { return }
        if let error = response.error {
            diagnostics.record("Worktree refresh for \(projectID.uuidString) failed with \(error.code) \(error.message)")
            return
        }
        if case let .worktrees(list) = response.result {
            worktrees = list
            projectWorktrees[projectID] = list
        } else {
            diagnostics.record(
                "Worktree refresh for \(projectID.uuidString) returned \(ConnectionDiagnostics.resultSummary(response.result))"
            )
        }
    }

    func refreshWorkspace(projectID: UUID) async {
        diagnostics.record("Refreshing workspace for project \(projectID.uuidString)")
        let params = GetWorkspaceParams(projectID: projectID)
        guard let response = await send(.getWorkspace, params: .getWorkspace(params)) else {
            if case .error = state {
                return
            }
            fail(
                "Could not open project session",
                operation: "Loading workspace",
                requestMethod: .getWorkspace,
                notes: ["Project ID: \(projectID.uuidString)"]
            )
            return
        }

        if response.error != nil {
            fail(
                "Could not open project session",
                operation: "Loading workspace",
                requestMethod: .getWorkspace,
                response: response,
                notes: ["Project ID: \(projectID.uuidString)"]
            )
            return
        }

        guard case let .workspace(ws) = response.result else {
            fail(
                "Could not open project session",
                operation: "Loading workspace",
                requestMethod: .getWorkspace,
                requestID: response.id,
                notes: [
                    "Project ID: \(projectID.uuidString)",
                    "Expected result: workspace",
                    "Actual result: \(ConnectionDiagnostics.resultSummary(response.result))",
                ]
            )
            return
        }

        workspace = ws
    }

    func createTab(projectID: UUID, areaID: UUID? = nil) async {
        let params = CreateTabParams(projectID: projectID, areaID: areaID)
        _ = await send(.createTab, params: .createTab(params))
        await refreshWorkspace(projectID: projectID)
    }

    func selectTab(projectID: UUID, areaID: UUID, tabID: UUID) async {
        let params = SelectTabParams(projectID: projectID, areaID: areaID, tabID: tabID)
        _ = await send(.selectTab, params: .selectTab(params))
        await refreshWorkspace(projectID: projectID)
    }

    func closeTab(projectID: UUID, areaID: UUID, tabID: UUID) async {
        let params = CloseTabParams(projectID: projectID, areaID: areaID, tabID: tabID)
        _ = await send(.closeTab, params: .closeTab(params))
        await refreshWorkspace(projectID: projectID)
    }

    func sendTerminalInput(paneID: UUID, bytes: Data) {
        let params = TerminalInputParams(paneID: paneID, bytes: bytes)
        sendFireAndForget(.terminalInput, params: .terminalInput(params))
    }

    private func sendFireAndForget(_ method: MuxyMethod, params: MuxyParams) {
        if let demo {
            if case let .terminalInput(p) = params {
                demo.handleTerminalInput(paneID: p.paneID, bytes: p.bytes)
            }
            return
        }
        guard let connection else { return }
        let request = MuxyRequest(id: UUID().uuidString, method: method, params: params)
        let message = MuxyMessage.request(request)
        guard let data = try? MuxyCodec.encode(message),
              let text = String(data: data, encoding: .utf8)
        else { return }
        connection.send(.string(text)) { _ in }
    }

    func resizeTerminal(paneID: UUID, cols: UInt32, rows: UInt32) async {
        let params = TerminalResizeParams(paneID: paneID, cols: cols, rows: rows)
        _ = await send(.terminalResize, params: .terminalResize(params))
    }

    func scrollTerminal(paneID: UUID, deltaX: Double, deltaY: Double, precise: Bool) async {
        let params = TerminalScrollParams(paneID: paneID, deltaX: deltaX, deltaY: deltaY, precise: precise)
        _ = await send(.terminalScroll, params: .terminalScroll(params))
    }

    func getTerminalCells(paneID: UUID) async -> TerminalCellsDTO? {
        let params = GetTerminalContentParams(paneID: paneID)
        guard let response = await send(.getTerminalContent, params: .getTerminalContent(params)) else { return nil }
        if case let .terminalCells(cells) = response.result {
            return cells
        }
        return nil
    }

    private static let voidMethods: Set<MuxyMethod> = [.terminalInput]

    func send(
        _ method: MuxyMethod,
        params: MuxyParams? = nil,
        timeout: Duration = .seconds(10)
    ) async -> MuxyResponse? {
        assert(!Self.voidMethods.contains(method), "\(method.rawValue) is fire-and-forget; use sendFireAndForget")
        if Self.voidMethods.contains(method) {
            if let params { sendFireAndForget(method, params: params) }
            return nil
        }
        if let demo {
            try? await Task.sleep(for: demo.simulatedDelay(for: method))
            return demo.handle(method, params: params)
        }
        let id = UUID().uuidString
        let request = MuxyRequest(id: id, method: method, params: params)
        let message = MuxyMessage.request(request)

        let data: Data

        do {
            data = try MuxyCodec.encode(message)
        } catch {
            fail(
                "Could not prepare request",
                operation: "Encoding \(method.rawValue) request",
                requestMethod: method,
                requestID: id,
                underlyingError: error
            )
            return nil
        }

        guard let text = String(data: data, encoding: .utf8) else {
            fail(
                "Could not prepare request",
                operation: "Encoding \(method.rawValue) request",
                requestMethod: method,
                requestID: id,
                notes: ["The encoded request was not valid UTF-8."]
            )
            return nil
        }

        guard let connection else {
            if case .disconnected = state { return nil }
            fail(
                "Could not reach device",
                operation: "Sending \(method.rawValue) request",
                requestMethod: method,
                requestID: id,
                notes: ["The WebSocket connection was nil before the request was sent."]
            )
            return nil
        }

        diagnostics.record("→ \(method.rawValue) [\(id)]")

        do {
            try await connection.send(.string(text))
        } catch {
            logger.error("Send failed: \(error)")
            if !isBackgrounded {
                fail(
                    "Connection lost",
                    operation: "Sending \(method.rawValue) request",
                    requestMethod: method,
                    requestID: id,
                    underlyingError: error
                )
            }
            return nil
        }

        return await withCheckedContinuation { continuation in
            pendingRequests[id] = PendingRequest(method: method, continuation: continuation)
            Task {
                try? await Task.sleep(for: timeout)
                if let pending = pendingRequests.removeValue(forKey: id) {
                    diagnostics.record("× \(pending.method.rawValue) [\(id)] timed out")
                    pending.continuation.resume(returning: MuxyResponse(id: id, error: MuxyError(code: 408, message: "Timeout")))
                }
            }
        }
    }

    private func receiveLoop() {
        connection?.receive { [weak self] result in
            DispatchQueue.main.async {
                MainActor.assumeIsolated {
                    guard let self else { return }
                    switch result {
                    case let .success(message):
                        self.handleMessage(message)
                        self.receiveLoop()
                    case let .failure(error):
                        switch self.state {
                        case .disconnected,
                             .error:
                            return
                        case .connecting,
                             .awaitingApproval:
                            logger.error("Connect failed: \(error)")
                            self.fail("Could not reach device", operation: "Opening WebSocket", underlyingError: error)
                        case .connected:
                            logger.error("Receive failed: \(error)")
                            if !self.isBackgrounded {
                                self.fail("Connection lost", operation: "Receiving WebSocket message", underlyingError: error)
                            }
                        }
                    }
                }
            }
        }
    }

    private func handleMessage(_ message: URLSessionWebSocketTask.Message) {
        let data: Data
        switch message {
        case let .string(text): data = Data(text.utf8)
        case let .data(d): data = d
        @unknown default: return
        }

        let muxyMessage: MuxyMessage

        do {
            muxyMessage = try MuxyCodec.decode(data)
        } catch {
            diagnostics.record("Failed to decode incoming message: \(ConnectionDiagnostics.inlineErrorDescription(error))")
            return
        }

        switch muxyMessage {
        case let .response(response):
            if let pending = pendingRequests.removeValue(forKey: response.id) {
                diagnostics.record("← \(pending.method.rawValue) [\(response.id)] \(ConnectionDiagnostics.responseSummary(response))")
                pending.continuation.resume(returning: response)
            }
        case let .event(event):
            handleEvent(event)
        case .request:
            break
        }
    }

    private func handleEvent(_ event: MuxyEvent) {
        switch event.data {
        case let .projects(list):
            diagnostics.record("Event \(event.event.rawValue): projects(\(list.count))")
            projects = list
        case let .workspace(ws):
            diagnostics.record("Event \(event.event.rawValue): workspace(project=\(ws.projectID.uuidString))")
            workspace = ws
        case let .notification(notification):
            diagnostics.record("Event \(event.event.rawValue): notification(\(notification.id.uuidString))")
            notifications.insert(notification, at: 0)
        case let .paneOwnership(dto):
            diagnostics.record("Event \(event.event.rawValue): paneOwnership(\(dto.paneID.uuidString))")
            paneOwners[dto.paneID] = dto.owner
        case let .deviceTheme(dto):
            diagnostics.record("Event \(event.event.rawValue): deviceTheme(fg=\(dto.fg), bg=\(dto.bg))")
            deviceTheme = DeviceTheme(fg: dto.fg, bg: dto.bg, palette: dto.palette ?? [])
        case let .terminalOutput(dto):
            terminalByteHandlers[dto.paneID]?(dto.bytes)
        case let .terminalSnapshot(dto):
            terminalByteHandlers[dto.paneID]?(dto.bytes)
        case .tab:
            break
        }
    }

    private func fail(
        _ message: String,
        operation: String,
        requestMethod: MuxyMethod? = nil,
        requestID: String? = nil,
        response: MuxyResponse? = nil,
        underlyingError: Error? = nil,
        notes: [String] = []
    ) {
        diagnostics.record("Failure during \(operation): \(message)")
        if case .disconnected = state { return }
        state = .error(
            diagnostics.makeIssue(
                message: message,
                operation: operation,
                stateSummary: Self.stateSummary(state),
                deviceName: lastDeviceName,
                host: lastHost,
                port: lastPort,
                requestMethod: requestMethod,
                requestID: requestID ?? response?.id,
                response: response,
                underlyingError: underlyingError,
                notes: notes
            )
        )
    }

    private static func stateSummary(_ state: State) -> String {
        switch state {
        case .disconnected:
            "disconnected"
        case .connecting:
            "connecting"
        case .awaitingApproval:
            "awaitingApproval"
        case .connected:
            "connected"
        case let .error(issue):
            "error(\(issue.message))"
        }
    }

    fileprivate static let demoModeKey = "demoMode"

    func addDevice(name: String, host: String, port: UInt16) {
        let device = SavedDevice(name: name, host: host, port: port)
        savedDevices.removeAll { $0.host == host && $0.port == port }
        savedDevices.insert(device, at: 0)
        if let demo {
            demo.addDevice(name: name, host: host, port: port)
        } else {
            SavedDevicesStore.save(savedDevices)
        }
    }

    func removeDevice(_ device: SavedDevice) {
        savedDevices.removeAll { $0.id == device.id }
        if let demo {
            demo.removeDevice(device)
        } else {
            SavedDevicesStore.save(savedDevices)
        }
    }
}
