import Testing
@testable import Muxy

struct ConnectionInputValidatorTests {
    private let validator = ConnectionInputValidator()

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

    @Test func validateSSHAcceptsPasswordInput() throws {
        let result = validator.validateSSH(
            name: "  Box  ",
            host: " box.local ",
            portText: " 22 ",
            username: "  root  ",
            authMethod: .password,
            secret: "hunter2",
            passphrase: ""
        )
        let input = try result.get()
        #expect(input.name == "Box")
        #expect(input.host == "box.local")
        #expect(input.port == 22)
        #expect(input.username == "root")
        #expect(input.authMethod == .password)
        #expect(input.secret == "hunter2")
        #expect(input.passphrase == nil)
    }

    @Test func validateSSHPreservesSecretAndPassphraseVerbatim() throws {
        let input = try validator.validateSSH(
            name: "Box",
            host: "box.local",
            portText: "22",
            username: "root",
            authMethod: .privateKey,
            secret: "-----BEGIN KEY-----\nline\n-----END KEY-----\n",
            passphrase: " pass phrase "
        ).get()
        #expect(input.secret == "-----BEGIN KEY-----\nline\n-----END KEY-----\n")
        #expect(input.passphrase == " pass phrase ")
    }

    @Test func validateSSHTreatsWhitespacePassphraseAsNil() throws {
        let input = try validator.validateSSH(
            name: "Box",
            host: "box.local",
            portText: "22",
            username: "root",
            authMethod: .privateKey,
            secret: "key",
            passphrase: "   "
        ).get()
        #expect(input.passphrase == nil)
    }

    @Test func validateSSHRejectsEmptyUsername() {
        let result = validator.validateSSH(
            name: "Box",
            host: "box.local",
            portText: "22",
            username: "   ",
            authMethod: .password,
            secret: "pw",
            passphrase: ""
        )
        #expect(result == .failure(.emptyUsername))
    }

    @Test func validateSSHRejectsWhitespacePassword() {
        let result = validator.validateSSH(
            name: "Box",
            host: "box.local",
            portText: "22",
            username: "root",
            authMethod: .password,
            secret: "   ",
            passphrase: ""
        )
        #expect(result == .failure(.emptyPassword))
    }

    @Test func validateSSHRejectsWhitespacePrivateKey() {
        let result = validator.validateSSH(
            name: "Box",
            host: "box.local",
            portText: "22",
            username: "root",
            authMethod: .privateKey,
            secret: "  \n  ",
            passphrase: ""
        )
        #expect(result == .failure(.emptyPrivateKey))
    }

    @Test func validateSSHReportsEndpointErrorsBeforeCredentials() {
        let result = validator.validateSSH(
            name: "Box",
            host: "bad host",
            portText: "22",
            username: "",
            authMethod: .password,
            secret: "",
            passphrase: ""
        )
        #expect(result == .failure(.invalidHost))
    }
}
