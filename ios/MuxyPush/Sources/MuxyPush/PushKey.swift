import CryptoKit
import Foundation

public struct PushKey: Sendable, Equatable {
    public static let byteCount = 32
    static let identifierLength = 8
    private static let identifierLabel = Data("muxy-push-key-id".utf8)
    private static let derivedLength = 16

    public let serverId: String
    let bytes: Data

    public init?(serverId: String, bytes: Data) {
        guard bytes.count == Self.byteCount, !serverId.isEmpty else { return nil }
        self.serverId = serverId
        self.bytes = bytes
    }

    public var keyID: Data {
        Data(SHA256.hash(data: Self.identifierLabel + bytes).prefix(Self.identifierLength))
    }

    public func collapseID(sessionId: UInt64) -> String {
        derive("collapse:\(sessionId)")
    }

    public func threadID(projectId: String) -> String {
        derive("thread:\(projectId)")
    }

    var symmetricKey: SymmetricKey {
        SymmetricKey(data: bytes)
    }

    private func derive(_ label: String) -> String {
        let code = HMAC<SHA256>.authenticationCode(for: Data(label.utf8), using: symmetricKey)
        return String(Base64URL.encode(Data(code)).prefix(Self.derivedLength))
    }
}

extension PushKey: CustomStringConvertible, CustomDebugStringConvertible {
    public var description: String {
        "PushKey(\(serverId))"
    }

    public var debugDescription: String {
        description
    }
}
