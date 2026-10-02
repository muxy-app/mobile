import Foundation
import MuxyMobile
import Testing
@testable import Muxy

@MainActor
struct ServerPairingModelTests {
    private let target = PairingLink(hosts: ["192.168.1.20", "studio.local"], port: 7419)

    private func makeModel(
        parsed: Result<PairingLink, MobileError>? = nil,
        paired: Result<ServerCredential, MobileError> = .success(Fixtures.credential()),
        credentials: InMemoryCredentialStore = InMemoryCredentialStore(),
        connections: InMemoryConnectionStore = InMemoryConnectionStore()
    ) -> ServerPairingModel {
        ServerPairingModel(
            pairing: StubPairingService(parsed: parsed ?? .success(target), paired: paired),
            credentials: credentials,
            store: connections,
            deviceName: "iPhone"
        )
    }

    @Test func aValidLinkAsksToConfirmTheFirstAddress() {
        let model = makeModel()
        model.receive(link: Fixtures.link, source: .qr)
        #expect(model.step == .confirm(PairingTarget(link: Fixtures.link, address: "192.168.1.20:7419", source: .qr)))
        #expect(model.failure == nil)
        #expect(model.canPair)
    }

    @Test func aForeignCodeShowsTheSpecMessage() {
        let model = makeModel(parsed: .failure(.InvalidLink))
        model.receive(link: "https://example.com", source: .manual)
        #expect(model.step == .entry)
        #expect(model.failure == "This isn't a Muxy pairing code.")
    }

    @Test func aBlankDeviceNameCannotPair() {
        let model = makeModel()
        model.receive(link: Fixtures.link, source: .qr)
        model.deviceName = "   "
        #expect(!model.canPair)
    }

    @Test func pairingSavesTheCredential() async throws {
        let credentials = InMemoryCredentialStore()
        let model = makeModel(credentials: credentials)
        model.receive(link: Fixtures.link, source: .qr)
        let connection = await model.pair()
        #expect(connection?.serverID == Fixtures.credential().serverId)
        #expect(try credentials.credential(serverId: Fixtures.credential().serverId) == Fixtures.credential())
    }

    @Test func anExpiredCodeAsksForANewOne() async {
        let model = makeModel(paired: .failure(.Unauthorized))
        model.receive(link: Fixtures.link, source: .qr)
        let connection = await model.pair()
        #expect(connection == nil)
        #expect(model.failure?.contains("Show a new code on your computer.") == true)
        #expect(model.step == .confirm(PairingTarget(link: Fixtures.link, address: "192.168.1.20:7419", source: .qr)))
    }

    @Test func aFailedSaveDoesNotReportSuccess() async {
        let model = makeModel(credentials: InMemoryCredentialStore(failsSaving: true))
        model.receive(link: Fixtures.link, source: .qr)
        #expect(await model.pair() == nil)
        #expect(model.failure != nil)
    }

    @Test func pairingWithoutAConfirmedLinkDoesNothing() async {
        #expect(await makeModel().pair() == nil)
    }
}
