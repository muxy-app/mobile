import Foundation
import MuxyMobile

nonisolated enum ServerFailure: Equatable, Sendable {
    case invalidLink
    case invalidCredential
    case unreachable
    case identityMismatch
    case unauthorized
    case incompatibleVersion
    case timeout
    case disconnected
    case server(String)
    case unknown

    enum Context: Sendable {
        case pairing
        case connecting
        case request
    }

    init(_ error: any Error) {
        guard let error = error as? MobileError else {
            self = .unknown
            return
        }
        switch error {
        case .InvalidLink:
            self = .invalidLink
        case .InvalidCredential:
            self = .invalidCredential
        case .Unreachable:
            self = .unreachable
        case .IdentityMismatch:
            self = .identityMismatch
        case .Unauthorized:
            self = .unauthorized
        case .IncompatibleVersion:
            self = .incompatibleVersion
        case .Timeout:
            self = .timeout
        case .Disconnected:
            self = .disconnected
        case let .Server(reason):
            self = .server(reason)
        }
    }

    var isFatal: Bool {
        switch self {
        case .invalidCredential, .identityMismatch, .unauthorized, .incompatibleVersion:
            return true
        case .invalidLink, .unreachable, .timeout, .disconnected, .server, .unknown:
            return false
        }
    }

    var requiresPairing: Bool {
        switch self {
        case .invalidCredential, .identityMismatch, .unauthorized:
            return true
        case .invalidLink, .unreachable, .incompatibleVersion, .timeout, .disconnected, .server, .unknown:
            return false
        }
    }

    func message(context: Context, serverName: String) -> String {
        switch self {
        case .invalidLink:
            return "This isn't a Muxy pairing code."
        case .invalidCredential:
            return "The saved pairing for \(serverName) is damaged. Pair this phone again."
        case .unreachable:
            return unreachableMessage(context: context, serverName: serverName)
        case .identityMismatch:
            return identityMismatchMessage(context: context)
        case .unauthorized:
            return unauthorizedMessage(context: context, serverName: serverName)
        case .incompatibleVersion:
            return "Update Muxy on your phone or computer. During the beta, both must come from the same Muxy build."
        case .timeout:
            return "Muxy isn't responding."
        case .disconnected:
            return "Disconnected from \(serverName)."
        case let .server(reason):
            return reason
        case .unknown:
            return "Something went wrong. Try again."
        }
    }

    private func unreachableMessage(context: Context, serverName: String) -> String {
        guard context == .pairing else {
            return "Can't reach \(serverName). Check that it's awake, that mobile access is on, and that it's on the same network or VPN."
        }
        return "Can't reach the computer. Check that it's awake and on the same network or VPN, and allow local network access if iOS asks."
    }

    private func identityMismatchMessage(context: Context) -> String {
        guard context == .pairing else {
            return "This computer's identity changed. Pair again."
        }
        return "The computer that answered isn't the one showing this code."
    }

    private func unauthorizedMessage(context: Context, serverName: String) -> String {
        guard context == .pairing else {
            return "This phone isn't paired with \(serverName) anymore."
        }
        return "This code expired, was already used, or was replaced. Show a new code on your computer."
    }
}
