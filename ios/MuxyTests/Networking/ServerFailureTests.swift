import Foundation
import MuxyMobile
import Testing
@testable import Muxy

struct ServerFailureTests {
    @Test func mapsEveryMobileError() {
        #expect(ServerFailure(MobileError.InvalidLink) == .invalidLink)
        #expect(ServerFailure(MobileError.InvalidCredential) == .invalidCredential)
        #expect(ServerFailure(MobileError.Unreachable(reason: "refused")) == .unreachable)
        #expect(ServerFailure(MobileError.IdentityMismatch) == .identityMismatch)
        #expect(ServerFailure(MobileError.Unauthorized) == .unauthorized)
        #expect(ServerFailure(MobileError.IncompatibleVersion) == .incompatibleVersion)
        #expect(ServerFailure(MobileError.Timeout) == .timeout)
        #expect(ServerFailure(MobileError.Disconnected) == .disconnected)
        #expect(ServerFailure(MobileError.Server(reason: "Unknown project.")) == .server("Unknown project."))
    }

    @Test func errorsFromOutsideTheSDKAreUnknown() {
        #expect(ServerFailure(CocoaError(.fileNoSuchFile)) == .unknown)
    }

    @Test(arguments: [ServerFailure.invalidCredential, .identityMismatch, .unauthorized, .incompatibleVersion])
    func failuresOnlyTheUserCanFixAreFatal(_ failure: ServerFailure) {
        #expect(failure.isFatal)
    }

    @Test(arguments: [ServerFailure.unreachable, .timeout, .disconnected, .server("busy"), .unknown, .invalidLink])
    func transientFailuresAreRetried(_ failure: ServerFailure) {
        #expect(!failure.isFatal)
    }

    @Test func onlyIdentityProblemsAskToPairAgain() {
        #expect(ServerFailure.unauthorized.requiresPairing)
        #expect(ServerFailure.identityMismatch.requiresPairing)
        #expect(ServerFailure.invalidCredential.requiresPairing)
        #expect(!ServerFailure.incompatibleVersion.requiresPairing)
        #expect(!ServerFailure.unreachable.requiresPairing)
    }

    @Test func unauthorizedMeansAnExpiredCodeWhilePairing() {
        let message = ServerFailure.unauthorized.message(context: .pairing, serverName: "Studio")
        #expect(message.contains("Show a new code on your computer."))
    }

    @Test func unauthorizedMeansRevokedWhenConnecting() {
        let message = ServerFailure.unauthorized.message(context: .connecting, serverName: "Studio")
        #expect(message == "This phone isn't paired with Studio anymore.")
    }

    @Test func identityMismatchWhilePairingPointsAtTheWrongComputer() {
        let message = ServerFailure.identityMismatch.message(context: .pairing, serverName: "Studio")
        #expect(message == "The computer that answered isn't the one showing this code.")
    }

    @Test func unreachableNamesTheComputer() {
        let message = ServerFailure.unreachable.message(context: .connecting, serverName: "Studio")
        #expect(message.hasPrefix("Can't reach Studio."))
    }

    @Test func invalidLinkMatchesTheSpecText() {
        #expect(ServerFailure.invalidLink.message(context: .pairing, serverName: "") == "This isn't a Muxy pairing code.")
    }

    @Test func serverReasonIsShownAsIs() {
        #expect(ServerFailure.server("No such project").message(context: .request, serverName: "Studio") == "No such project")
    }
}
