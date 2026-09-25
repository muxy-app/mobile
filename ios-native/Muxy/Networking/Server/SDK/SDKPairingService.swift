import Foundation
import MuxyMobile

nonisolated struct SDKPairingService: ServerPairingService {
    func parse(_ link: String) throws -> PairingLink {
        try parsePairingLink(link: link)
    }

    func pair(link: String, deviceName: String) async throws -> ServerCredential {
        try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                continuation.resume(with: Result { try MuxyMobile.pair(link: link, deviceName: deviceName) })
            }
        }
    }
}
