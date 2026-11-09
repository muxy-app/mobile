import CryptoKit
import Foundation

struct PushPayload: Sendable {
    static let version: UInt8 = 1
    private static let headerLength = 1 + PushKey.identifierLength
    private static let nonceLength = 12
    private static let tagLength = 16
    private static let minimumLength = headerLength + nonceLength + tagLength + 1

    let header: Data
    let nonce: Data
    let ciphertext: Data
    let tag: Data

    init?(_ encoded: String) {
        guard let bytes = Base64URL.decode(encoded).map({ Data($0) }) else { return nil }
        guard bytes.count >= Self.minimumLength, bytes.first == Self.version else { return nil }
        let nonceEnd = Self.headerLength + Self.nonceLength
        let tagStart = bytes.count - Self.tagLength
        header = bytes.subdata(in: 0..<Self.headerLength)
        nonce = bytes.subdata(in: Self.headerLength..<nonceEnd)
        ciphertext = bytes.subdata(in: nonceEnd..<tagStart)
        tag = bytes.subdata(in: tagStart..<bytes.count)
    }

    var keyID: Data {
        header.dropFirst()
    }

    func open(with key: PushKey) -> Data? {
        guard let sealed = try? AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: nonce), ciphertext: ciphertext, tag: tag) else {
            return nil
        }
        return try? AES.GCM.open(sealed, using: key.symmetricKey, authenticating: header + Data(key.serverId.utf8))
    }
}
