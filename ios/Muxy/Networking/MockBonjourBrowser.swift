import Foundation
import Observation

@MainActor
@Observable
final class MockBonjourBrowser: BonjourBrowsing {
    private(set) var services: [DiscoveredService]
    private(set) var isBrowsing = false

    init(services: [DiscoveredService] = []) {
        self.services = services
    }

    func start() {
        isBrowsing = true
    }

    func stop() {
        isBrowsing = false
    }

    func set(services: [DiscoveredService]) {
        self.services = services
    }
}
