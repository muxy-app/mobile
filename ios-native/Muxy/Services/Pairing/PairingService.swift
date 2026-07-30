import Foundation
import OSLog

protocol PairingService: Sendable {
    func pair(
        using client: MuxyClient,
        params: AuthParams,
        onStatus: @escaping @Sendable (PairingStatus) -> Void
    ) async -> PairingStatus
}

struct LivePairingService: PairingService {
    func pair(
        using client: MuxyClient,
        params: AuthParams,
        onStatus: @escaping @Sendable (PairingStatus) -> Void
    ) async -> PairingStatus {
        onStatus(.authenticating)

        do {
            let pairing = try await authenticate(using: client, params: params)
            return finish(.paired(pairing), onStatus: onStatus)
        } catch let error as ProtocolError {
            return await handleAuthenticationError(error, using: client, params: params, onStatus: onStatus)
        } catch {
            return finish(.failed(.connectionFailed), onStatus: onStatus)
        }
    }

    private func handleAuthenticationError(
        _ error: ProtocolError,
        using client: MuxyClient,
        params: AuthParams,
        onStatus: @escaping @Sendable (PairingStatus) -> Void
    ) async -> PairingStatus {
        guard error.code == .unauthorized else {
            if error.code == .forbidden {
                return finish(.failed(.wrongToken), onStatus: onStatus)
            }
            return finish(.failed(mapped(error)), onStatus: onStatus)
        }

        onStatus(.awaitingApproval)
        do {
            let pairing = try await requestPairing(using: client, params: params)
            return finish(.paired(pairing), onStatus: onStatus)
        } catch let pairError as ProtocolError {
            return finish(.failed(mapped(pairError)), onStatus: onStatus)
        } catch {
            return finish(.failed(.connectionFailed), onStatus: onStatus)
        }
    }

    private func authenticate(using client: MuxyClient, params: AuthParams) async throws -> Pairing {
        let result = try await client.request(.authenticateDevice, params: params)
        return try pairing(from: result)
    }

    private func requestPairing(using client: MuxyClient, params: AuthParams) async throws -> Pairing {
        let result = try await client.request(.pairDevice, params: params)
        return try pairing(from: result)
    }

    private func pairing(from result: RawTagged) throws -> Pairing {
        guard result.type == ResultType.pairing else { throw PairingError.invalidResponse }
        return try result.decode(PairingResult.self).pairing
    }

    private func mapped(_ error: ProtocolError) -> PairingError {
        switch error.code {
        case .forbidden:
            return .approvalDenied
        case .pairingTimeout:
            return .approvalTimedOut
        default:
            return .server(code: error.body.code, message: error.body.message)
        }
    }

    private func finish(
        _ status: PairingStatus,
        onStatus: @escaping @Sendable (PairingStatus) -> Void
    ) -> PairingStatus {
        Log.pairing.debug("Pairing finished: \(String(describing: status), privacy: .public)")
        onStatus(status)
        return status
    }
}
