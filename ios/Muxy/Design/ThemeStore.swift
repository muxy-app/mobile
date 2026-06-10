import Foundation
import Observation

@MainActor
@Observable
final class ThemeStore {
    private(set) var theme: AppTheme

    @ObservationIgnored private let connectionManager: ConnectionManager
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private var stateTask: Task<Void, Never>?
    @ObservationIgnored private var eventTask: Task<Void, Never>?

    init(connectionManager: ConnectionManager, defaults: UserDefaults = .standard) {
        self.connectionManager = connectionManager
        self.defaults = defaults
        theme = ThemeStore.loadPersistedTheme(from: defaults) ?? .muxy
    }

    func start() {
        startObservingState()
        startObservingEvents()
    }

    private func startObservingState() {
        guard stateTask == nil else { return }
        stateTask = Task { [weak self, connectionManager] in
            let states = await connectionManager.stateUpdates()
            for await _ in states {
                await self?.refresh()
            }
        }
    }

    private func startObservingEvents() {
        guard eventTask == nil else { return }
        eventTask = Task { [weak self, connectionManager] in
            let events = await connectionManager.events()
            for await event in events where event.event == EventName.themeChanged {
                await self?.refresh()
            }
        }
    }

    private func refresh() async {
        guard let event = await connectionManager.currentTheme else { return }
        theme = AppTheme(event: event)
        persist(event)
    }

    private func persist(_ event: DeviceThemeEvent) {
        guard let data = try? JSONEncoder().encode(event) else { return }
        defaults.set(data, forKey: ThemeStore.persistenceKey)
    }

    private static func loadPersistedTheme(from defaults: UserDefaults) -> AppTheme? {
        guard let data = defaults.data(forKey: persistenceKey) else { return nil }
        guard let event = try? JSONDecoder().decode(DeviceThemeEvent.self, from: data) else { return nil }
        return AppTheme(event: event)
    }

    private static let persistenceKey = "muxy.theme.lastDeviceTheme"
}
