import Foundation
import Testing
@testable import Muxy

@MainActor
struct ThemeStoreTests {
    private static func authReply(_ frame: String) -> [String] {
        guard
            let data = frame.data(using: .utf8),
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let payload = object["payload"] as? [String: Any],
            let id = payload["id"] as? String
        else { return [] }
        return ["""
        { "type": "response", "payload": { "id": "\(id)", "result": { "type": "pairing",
          "value": { "clientID": "c", "deviceName": "iPhone",
          "themeFg": 16777215, "themeBg": 1193046, "themePalette": null } } } }
        """]
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

    private func makeDefaults() -> UserDefaults {
        let suite = "muxy.themeStore.tests.\(UUID().uuidString)"
        return UserDefaults(suiteName: suite)!
    }

    private func waitForTheme(_ store: ThemeStore, toEqual expected: AppTheme) async {
        for _ in 0 ..< 100 {
            if store.theme == expected { return }
            try? await Task.sleep(for: .milliseconds(10))
        }
    }

    @Test func startsWithMuxyDefaultWhenNothingPersisted() {
        let manager = ConnectionManager(makeTransport: { _ in MockTransport(autoReply: { _ in [] }) })
        let store = ThemeStore(connectionManager: manager, defaults: makeDefaults())
        #expect(store.theme == .muxy)
    }

    private var expectedTheme: AppTheme {
        AppTheme(event: DeviceThemeEvent(fg: 0xFFFFFF, bg: 0x123456, palette: nil))
    }

    @Test func adoptsThemeAfterConnection() async {
        let manager = ConnectionManager(makeTransport: { _ in MockTransport(autoReply: ThemeStoreTests.authReply) })
        let store = ThemeStore(connectionManager: manager, defaults: makeDefaults())
        store.start()

        await manager.connect(to: device(), token: "t")
        await waitForTheme(store, toEqual: expectedTheme)

        #expect(store.theme == expectedTheme)
    }

    @Test func keepsThemeAfterDisconnect() async {
        let manager = ConnectionManager(makeTransport: { _ in MockTransport(autoReply: ThemeStoreTests.authReply) })
        let store = ThemeStore(connectionManager: manager, defaults: makeDefaults())
        store.start()

        await manager.connect(to: device(), token: "t")
        await waitForTheme(store, toEqual: expectedTheme)

        await manager.disconnect()
        await Task.yield()

        #expect(store.theme == expectedTheme)
        #expect(store.theme != .muxy)
    }

    @Test func restoresPersistedThemeOnNextLaunch() async {
        let defaults = makeDefaults()
        let manager = ConnectionManager(makeTransport: { _ in MockTransport(autoReply: ThemeStoreTests.authReply) })
        let store = ThemeStore(connectionManager: manager, defaults: defaults)
        store.start()

        await manager.connect(to: device(), token: "t")
        await waitForTheme(store, toEqual: expectedTheme)

        let relaunched = ThemeStore(connectionManager: manager, defaults: defaults)
        #expect(relaunched.theme == expectedTheme)
    }
}
