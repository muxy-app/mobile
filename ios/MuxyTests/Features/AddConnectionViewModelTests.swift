import Foundation
import MuxyMobile
import Testing
@testable import Muxy

@MainActor
struct AddConnectionViewModelTests {
    private func makeViewModel(
        services: [DiscoveredService] = [],
        parsed: Result<PairingLink, MobileError> = .failure(.InvalidLink)
    ) -> AddConnectionViewModel {
        AddConnectionViewModel(
            store: InMemoryConnectionStore(),
            keychain: InMemoryKeychainStore(),
            connectionManager: ConnectionManager(makeTransport: { _ in MockTransport() }),
            validator: ConnectionInputValidator(),
            tokenGenerator: TokenGenerator(),
            browser: MockBonjourBrowser(services: services),
            serverPairing: makeServerPairing(parsed: parsed)
        )
    }

    private func makeServerPairing(parsed: Result<PairingLink, MobileError> = .failure(.InvalidLink)) -> ServerPairingModel {
        ServerPairingModel(
            pairing: StubPairingService(parsed: parsed, paired: .failure(.InvalidLink)),
            credentials: InMemoryCredentialStore(),
            store: InMemoryConnectionStore(),
            deviceName: "iPhone"
        )
    }

    @Test func defaultsToMuxy2Pairing() {
        let viewModel = makeViewModel()
        #expect(viewModel.kind == .server)
        #expect(viewModel.serverTransport == .paired)
        #expect(viewModel.serverPairing.target == nil)
    }

    @Test func defaultsPortToDefault() {
        let viewModel = makeViewModel()
        #expect(viewModel.portText == String(Endpoint.defaultPort))
    }

    @Test func canSubmitIsFalseWhenEmpty() {
        let viewModel = makeViewModel()
        #expect(viewModel.canSubmit == false)
    }

    @Test func muxy1CanSubmitWithValidInput() {
        let viewModel = makeViewModel()
        viewModel.selectKind(.device)
        viewModel.name = "Studio"
        viewModel.host = "studio.local"
        #expect(viewModel.canSubmit)
    }

    @Test func muxy2RequiresAPairingCodeInsteadOfALegacyAddress() {
        let viewModel = makeViewModel()
        viewModel.name = "Studio"
        viewModel.host = "studio.local"
        #expect(!viewModel.canSubmit)
    }

    @Test func aMuxy2CodeOpensConfirmationWithoutPairing() {
        let viewModel = makeViewModel(parsed: .success(PairingLink(hosts: ["studio.local"], port: 7419)))
        #expect(viewModel.applyPairingCode(Fixtures.link, source: .qr))
        #expect(viewModel.kind == .server)
        #expect(viewModel.serverTransport == .paired)
        #expect(viewModel.serverPairing.step == .confirm(PairingTarget(link: Fixtures.link, address: "studio.local:7419", source: .qr)))
        #expect(viewModel.canSubmit)
        #expect(!viewModel.isWorking)
    }

    @Test func aMuxy1CodeStillOpensTheLegacyForm() {
        let viewModel = makeViewModel()
        #expect(viewModel.applyPairingCode("muxy://pair?host=studio.local&port=4865&label=Studio", source: .qr))
        #expect(viewModel.kind == .device)
        #expect(viewModel.name == "Studio")
        #expect(viewModel.host == "studio.local")
        #expect(viewModel.portText == "4865")
        #expect(viewModel.canSubmit)
    }

    @Test func applyScanPopulatesFieldsAndSource() {
        let viewModel = makeViewModel()
        viewModel.selectKind(.device)
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
        viewModel.selectKind(.device)
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
            browser: browser,
            serverPairing: makeServerPairing()
        )
        viewModel.startDiscovery()
        #expect(browser.isBrowsing)
        viewModel.stopDiscovery()
        #expect(browser.isBrowsing == false)
    }
}
