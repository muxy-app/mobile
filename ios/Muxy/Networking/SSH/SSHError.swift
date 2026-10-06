import Foundation

nonisolated enum SSHError: Error, Equatable, Sendable {
    case unreachable
    case authenticationFailed
    case hostKeyChanged
    case keyParseFailed
    case missingCredentials

    static func connectionFailure(_ error: any Error) -> SSHError {
        if let error = error as? SSHError { return error }
        let description = String(describing: error).lowercased()
        if description.contains("authentication") || description.contains("unauthorized") {
            return .authenticationFailed
        }
        return .unreachable
    }

    var message: String {
        switch self {
        case .unreachable:
            return "Couldn't reach the server. Check the host and port."
        case .authenticationFailed:
            return "Authentication failed. Check your username and credentials."
        case .hostKeyChanged:
            return "The server's host key has changed. Connection refused to protect against tampering."
        case .keyParseFailed:
            return "Couldn't read the private key. Check the key and passphrase."
        case .missingCredentials:
            return "Missing SSH credentials. Remove this connection and add it again."
        }
    }
}
