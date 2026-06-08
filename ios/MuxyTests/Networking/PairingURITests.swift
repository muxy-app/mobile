import Testing
@testable import Muxy

struct PairingURITests {
    @Test func parsesFullURI() throws {
        let uri = try PairingURI.parse("muxy://pair?host=studio.local&port=4865&service=Studio&label=My%20Mac")
        #expect(uri.host == "studio.local")
        #expect(uri.port == 4865)
        #expect(uri.serviceName == "Studio")
        #expect(uri.label == "My Mac")
    }

    @Test func defaultsPortWhenAbsent() throws {
        let uri = try PairingURI.parse("muxy://pair?host=studio.local&service=Studio")
        #expect(uri.port == Endpoint.defaultPort)
    }

    @Test func optionalFieldsAreNilWhenAbsent() throws {
        let uri = try PairingURI.parse("muxy://pair?host=studio.local")
        #expect(uri.serviceName == nil)
        #expect(uri.label == nil)
    }

    @Test(arguments: ["muxy://pair?host=studio.local&port=0",
                      "muxy://pair?host=studio.local&port=70000",
                      "muxy://pair?host=studio.local&port=abc"])
    func rejectsInvalidPort(_ string: String) {
        #expect(throws: PairingURIError.invalidPort) {
            try PairingURI.parse(string)
        }
    }

    @Test(arguments: ["https://pair?host=studio.local",
                      "muxy://connect?host=studio.local",
                      "ssh://pair?host=studio.local"])
    func rejectsNonMuxyScheme(_ string: String) {
        #expect(throws: PairingURIError.notMuxyScheme) {
            try PairingURI.parse(string)
        }
    }

    @Test func rejectsMissingHost() {
        #expect(throws: PairingURIError.missingHost) {
            try PairingURI.parse("muxy://pair?service=Studio")
        }
    }

    @Test func rejectsEmptyHost() {
        #expect(throws: PairingURIError.missingHost) {
            try PairingURI.parse("muxy://pair?host=")
        }
    }

    @Test func neverExtractsToken() throws {
        let uri = try PairingURI.parse("muxy://pair?host=studio.local&token=secret&port=4865")
        let mirror = Mirror(reflecting: uri)
        let hasTokenField = mirror.children.contains { $0.label == "token" }
        #expect(hasTokenField == false)
        #expect(uri.host == "studio.local")
        #expect(uri.port == 4865)
    }
}
