import Crypto
import Foundation
import NIOCore
import NIOSSH
import OSLog

nonisolated struct TOFUHostKeyValidator: NIOSSHClientServerAuthenticationDelegate, Sendable {
    private let connectionID: Connection.ID
    private let keychain: KeychainStore

    init(connectionID: Connection.ID, keychain: KeychainStore) {
        self.connectionID = connectionID
        self.keychain = keychain
    }

    func validateHostKey(hostKey: NIOSSHPublicKey, validationCompletePromise: EventLoopPromise<Void>) {
        let fingerprint = TOFUHostKeyValidator.fingerprint(of: hostKey)

        guard let pinned = (try? keychain.secret(.sshHostKey, for: connectionID)) ?? nil else {
            pin(fingerprint, promise: validationCompletePromise)
            return
        }

        guard pinned == fingerprint else {
            Log.ssh.error("Host key mismatch for \(connectionID.uuidString, privacy: .public)")
            validationCompletePromise.fail(SSHError.hostKeyChanged)
            return
        }

        validationCompletePromise.succeed(())
    }

    private func pin(_ fingerprint: String, promise: EventLoopPromise<Void>) {
        do {
            try keychain.setSecret(fingerprint, .sshHostKey, for: connectionID)
            Log.ssh.debug("Pinned host key for \(connectionID.uuidString, privacy: .public)")
            promise.succeed(())
        } catch {
            Log.ssh.error("Failed to pin host key: \(error.localizedDescription, privacy: .public)")
            promise.fail(SSHError.hostKeyChanged)
        }
    }

    static func fingerprint(of hostKey: NIOSSHPublicKey) -> String {
        var buffer = ByteBuffer()
        _ = hostKey.write(to: &buffer)
        let bytes = buffer.readBytes(length: buffer.readableBytes) ?? []
        let digest = SHA256.hash(data: Data(bytes))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
