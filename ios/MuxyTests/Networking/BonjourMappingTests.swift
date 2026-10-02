import Testing
@testable import Muxy

struct BonjourMappingTests {
    @Test func buildsServiceFromHostName() throws {
        let service = try #require(BonjourMapping.service(name: "Studio", hostName: "studio.local.", port: 4865))
        #expect(service.name == "Studio")
        #expect(service.host == "studio.local")
        #expect(service.port == 4865)
    }

    @Test func stripsTrailingDot() {
        #expect(BonjourMapping.normalizedHost("studio.local.") == "studio.local")
    }

    @Test func keepsHostWithoutTrailingDot() {
        #expect(BonjourMapping.normalizedHost("studio.local") == "studio.local")
    }

    @Test(arguments: ["localhost", "::1", "127.0.0.1", "0.0.0.0", "LOCALHOST"])
    func rejectsUnreachableHosts(_ host: String) {
        #expect(BonjourMapping.normalizedHost(host) == nil)
    }

    @Test func rejectsNilHost() {
        #expect(BonjourMapping.normalizedHost(nil) == nil)
        #expect(BonjourMapping.service(name: "Studio", hostName: nil, port: 4865) == nil)
    }

    @Test func rejectsEmptyName() {
        #expect(BonjourMapping.service(name: "   ", hostName: "studio.local", port: 4865) == nil)
    }

    @Test func rejectsLoopbackService() {
        #expect(BonjourMapping.service(name: "Studio", hostName: "::1", port: 4865) == nil)
    }

    @Test(arguments: [0, 65536, -1])
    func rejectsInvalidPort(_ port: Int) {
        #expect(BonjourMapping.service(name: "Studio", hostName: "studio.local", port: port) == nil)
    }

    @Test func usesDiscoveredServiceNameAsID() {
        let service = DiscoveredService(name: "Studio", host: "studio.local", port: 4865)
        #expect(service.id == "Studio")
    }
}
