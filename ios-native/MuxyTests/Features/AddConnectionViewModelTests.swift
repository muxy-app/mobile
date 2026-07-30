import Foundation
import Testing
@testable import Muxy

@MainActor
struct AddConnectionViewModelTests {
    private func makeViewModel(
        services: [DiscoveredService] = []
    ) -> AddConnectionViewModel {
        AddConnectionViewModel(
            store: InMemoryConnectionStore(),
            keychain: InMemoryKeychainStore(),
            connectionManager: ConnectionManager(makeTransport: { _ in MockTransport() }),
            validator: ConnectionInputValidator(),
            tokenGenerator: TokenGenerator(),
            browser: MockBonjourBrowser(services: services)
        )
    }

    @Test func defaultsPortToDefault() {
        let viewModel = makeViewModel()
        #expect(viewModel.portText == String(Endpoint.defaultPort))
    }

    @Test func canSubmitIsFalseWhenEmpty() {
        let viewModel = makeViewModel()
        #expect(viewModel.canSubmit == false)
    }

    @Test func canSubmitIsTrueWithValidInput() {
        let viewModel = makeViewModel()
        viewModel.name = "Studio"
        viewModel.host = "studio.local"
        #expect(viewModel.canSubmit)
    }

    @Test func applyScanPopulatesFieldsAndSource() {
        let viewModel = makeViewModel()
        let uri = PairingURI(host: "studio.local", port: 5000, serviceName: "Studio", label: "My Mac")
        viewModel.applyScan(uri)

        #expect(viewModel.host == "studio.local")
        #expect(viewModel.portText == "5000")
        #expect(viewModel.name == "My Mac")
        #expect(viewModel.discoverySource == .qr)
        #expect(viewModel.isShowingScanner == false)
    }

    @Test func applyDiscoveredPopulatesFieldsAndSource() {
        let viewModel = makeViewModel()
        let service = DiscoveredService(name: "Studio", host: "studio.local", port: 4865)
        viewModel.applyDiscovered(service)

        #expect(viewModel.name == "Studio")
        #expect(viewModel.host == "studio.local")
        #expect(viewModel.portText == "4865")
        #expect(viewModel.discoverySource == .bonjour)
    }

    @Test func exposesDiscoveredServices() {
        let service = DiscoveredService(name: "Studio", host: "studio.local", port: 4865)
        let viewModel = makeViewModel(services: [service])
        #expect(viewModel.discoveredServices == [service])
    }

    @Test func startDiscoveryStartsBrowser() {
        let browser = MockBonjourBrowser()
        let viewModel = AddConnectionViewModel(
            store: InMemoryConnectionStore(),
            keychain: InMemoryKeychainStore(),
            connectionManager: ConnectionManager(makeTransport: { _ in MockTransport() }),
            validator: ConnectionInputValidator(),
            tokenGenerator: TokenGenerator(),
            browser: browser
        )
        viewModel.startDiscovery()
        #expect(browser.isBrowsing)
        viewModel.stopDiscovery()
        #expect(browser.isBrowsing == false)
    }
}
