import Foundation
import Security

protocol TokenGenerating: Sendable {
    func generate() -> String
}

struct TokenGenerator: TokenGenerating {
    private let byteCount: Int

    init(byteCount: Int = 32) {
        self.byteCount = byteCount
    }

    func generate() -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        let status = SecRandomCopyBytes(kSecRandomDefault, byteCount, &bytes)
        guard status == errSecSuccess else {
            return UUID().uuidString
        }
        return Data(bytes).base64URLEncodedString()
    }
}

private extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
