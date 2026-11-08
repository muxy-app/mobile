import Foundation
import MuxyPush
import Testing
@testable import Muxy

struct PushDisplayTests {
    private let fallback = PushAlert(title: "Muxy", body: "An agent needs your attention")
    private let studio = PushServer(serverId: PushVectors.serverID, name: "Studio")
    private let laptop = PushServer(serverId: "laptop", name: "Laptop")

    @Test func authenticatedPayloadsShowTheirText() {
        let alert = PushDisplay.alert(for: PushVectors.payload, kind: "attention", fallback: fallback, keys: [PushVectors.key], servers: [studio])
        #expect(alert.title == "Claude Code · muxy-website")
        #expect(alert.body == "Needs your attention")
        #expect(alert.subtitle == nil)
        #expect(alert.threadID == "aJnt975IClwhFwRP")
    }

    @Test func theServerNameIsAddedWhenSeveralServersArePaired() {
        let alert = PushDisplay.alert(for: PushVectors.payload, keys: [PushVectors.key], servers: [studio, laptop])
        #expect(alert?.subtitle == "Studio")
    }

    @Test func payloadsWithoutAKeyShowTheFallback() {
        let alert = PushDisplay.alert(for: PushVectors.payload, kind: "attention", fallback: fallback, keys: [], servers: [studio])
        #expect(alert == fallback)
    }

    @Test func forgedPayloadsShowTheFallbackAndNeverTheirBytes() {
        let forged = PushVectors.seal(PushVectors.message(), keyBytes: Data(repeating: 5, count: 32))
        let alert = PushDisplay.alert(for: forged, kind: "attention", fallback: fallback, keys: [PushVectors.key], servers: [studio])
        #expect(alert == fallback)
        #expect(!alert.title.contains(forged) && !alert.body.contains(forged))
    }

    @Test func otherVersionsShowTheFallback() {
        let sealed = PushVectors.seal(PushVectors.message(version: 2))
        #expect(PushDisplay.alert(for: sealed, kind: "completed", fallback: nil, keys: [PushVectors.key], servers: []) == PushKind.completed.fallback)
    }

    @Test func theKindPicksTheFallbackWhenNoneArrived() {
        #expect(PushDisplay.alert(for: nil, kind: "attention", fallback: nil, keys: [], servers: []).body == "An agent needs your attention")
        #expect(PushDisplay.alert(for: nil, kind: "completed", fallback: nil, keys: [], servers: []).body == "An agent finished working")
        #expect(PushDisplay.alert(for: "garbage", kind: "unknown", fallback: nil, keys: [], servers: []).title == "Muxy")
    }
}
