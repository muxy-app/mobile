import Foundation
import MuxyPush
import Testing
@testable import Muxy

struct PushPayloadTests {
    @Test func theKeyIDMatchesTheVector() {
        let hex = PushVectors.key.keyID.map { String(format: "%02x", $0) }.joined()
        #expect(hex == "627df844ff14f4ed")
    }

    @Test func collapseAndThreadIDsMatchTheVectors() {
        #expect(PushVectors.key.collapseID(sessionId: 42) == "usZ3J1QStv6L6omf")
        #expect(PushVectors.key.threadID(projectId: PushVectors.projectID) == "aJnt975IClwhFwRP")
    }

    @Test func sealingTheVectorReproducesThePayloadByteForByte() {
        #expect(PushVectors.seal(PushVectors.plaintext) == PushVectors.payload)
    }

    @Test func theVectorPayloadDecrypts() throws {
        let message = try #require(PushOpener.open(PushVectors.payload, keys: [PushVectors.key]))
        #expect(message.version == 1)
        #expect(message.serverId == PushVectors.serverID)
        #expect(message.projectId == PushVectors.projectID)
        #expect(message.sessionId == 42)
        #expect(message.eventId == 1_731_067_200_000_123)
        #expect(message.kind == "attention")
        #expect(message.title == "Claude Code · muxy-website")
        #expect(message.body == "Needs your attention")
        #expect(message.timestamp == 1_731_067_200)
        #expect(message.target == PushVectors.target)
    }

    @Test func anotherServerIDFailsToAuthenticate() throws {
        let other = try #require(PushKey(serverId: "other", bytes: PushVectors.keyBytes))
        #expect(PushOpener.open(PushVectors.payload, keys: [other]) == nil)
    }

    @Test func theKeyIsFoundByItsKeyIDAmongSeveral() throws {
        let unrelated = try #require(PushKey(serverId: "server-a", bytes: Data(repeating: 9, count: 32)))
        let another = try #require(PushKey(serverId: "server-b", bytes: Data(repeating: 3, count: 32)))
        let message = PushOpener.open(PushVectors.payload, keys: [unrelated, PushVectors.key, another])
        #expect(message?.serverId == PushVectors.serverID)
    }

    @Test func anUnknownKeyIDOpensNothing() throws {
        let unrelated = try #require(PushKey(serverId: PushVectors.serverID, bytes: Data(repeating: 9, count: 32)))
        #expect(PushOpener.open(PushVectors.payload, keys: [unrelated]) == nil)
        #expect(PushOpener.open(PushVectors.payload, keys: []) == nil)
    }

    @Test func aMessageNamingAnotherServerIsRejected() {
        let sealed = PushVectors.seal(PushVectors.message(serverID: "someone-else"))
        #expect(PushOpener.open(sealed, keys: [PushVectors.key]) == nil)
    }

    @Test func otherPlaintextVersionsAreRejected() {
        let sealed = PushVectors.seal(PushVectors.message(version: 2))
        #expect(PushOpener.open(sealed, keys: [PushVectors.key]) == nil)
    }

    @Test func unknownFieldsAreIgnored() {
        let sealed = PushVectors.seal(PushVectors.message())
        #expect(PushOpener.open(sealed, keys: [PushVectors.key])?.body == "Finished working")
    }

    @Test func malformedPayloadsOpenNothing() {
        let tampered = String(PushVectors.payload.dropLast()) + (PushVectors.payload.hasSuffix("A") ? "B" : "A")
        let otherVersion = "Ag" + PushVectors.payload.dropFirst(2)
        let malformed = ["", "not base64!", String(PushVectors.payload.prefix(40)), tampered, otherVersion, PushVectors.payload + "="]
        for payload in malformed {
            #expect(PushOpener.open(payload, keys: [PushVectors.key]) == nil)
        }
    }

    @Test func keysMustBeThirtyTwoBytesAndNamed() {
        #expect(PushKey(serverId: PushVectors.serverID, bytes: Data(repeating: 1, count: 31)) == nil)
        #expect(PushKey(serverId: "", bytes: PushVectors.keyBytes) == nil)
    }

    @Test func keysNeverDescribeTheirBytes() {
        let description = String(describing: PushVectors.key)
        #expect(description == "PushKey(\(PushVectors.serverID))")
    }
}
