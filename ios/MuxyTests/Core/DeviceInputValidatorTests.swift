import Testing
@testable import Muxy

struct DeviceInputValidatorTests {
    private let validator = DeviceInputValidator()

    @Test func acceptsValidInput() throws {
        let result = validator.validate(name: "Studio", host: "studio.local", portText: "4865")
        let input = try result.get()
        #expect(input.name == "Studio")
        #expect(input.host == "studio.local")
        #expect(input.port == 4865)
    }

    @Test func trimsWhitespace() throws {
        let result = validator.validate(name: "  Studio  ", host: "  studio.local ", portText: " 4865 ")
        let input = try result.get()
        #expect(input.name == "Studio")
        #expect(input.host == "studio.local")
        #expect(input.port == 4865)
    }

    @Test func rejectsEmptyName() {
        #expect(validator.validate(name: "   ", host: "studio.local", portText: "4865") == .failure(.emptyName))
    }

    @Test func rejectsEmptyHost() {
        #expect(validator.validate(name: "Studio", host: "  ", portText: "4865") == .failure(.emptyHost))
    }

    @Test func rejectsHostWithSpaces() {
        #expect(validator.validate(name: "Studio", host: "stud io.local", portText: "4865") == .failure(.invalidHost))
    }

    @Test func rejectsMissingPort() {
        #expect(validator.validate(name: "Studio", host: "studio.local", portText: "") == .failure(.missingPort))
    }

    @Test(arguments: ["0", "65536", "abc", "-1", "70000"])
    func rejectsInvalidPort(_ portText: String) {
        #expect(validator.validate(name: "Studio", host: "studio.local", portText: portText) == .failure(.invalidPort))
    }

    @Test(arguments: ["1", "65535", "192.168.1.10", "host-name.local"])
    func acceptsBoundaryAndHostShapes(_ value: String) throws {
        let asPort = validator.validate(name: "N", host: "studio.local", portText: value)
        let asHost = validator.validate(name: "N", host: value, portText: "4865")
        #expect((try? asPort.get()) != nil || (try? asHost.get()) != nil)
    }

    @Test func acceptsIPv4Host() throws {
        let input = try validator.validate(name: "N", host: "192.168.1.10", portText: "4865").get()
        #expect(input.host == "192.168.1.10")
    }
}
