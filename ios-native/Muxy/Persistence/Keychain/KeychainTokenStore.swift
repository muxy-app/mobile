import Foundation
import Security

struct KeychainTokenStore: KeychainStore {
    private let service: String

    init(service: String = "com.muxy.app.tokens") {
        self.service = service
    }

    func setSecret(_ value: String, _ secret: KeychainSecret, for connectionID: Connection.ID) throws {
        guard let data = value.data(using: .utf8) else { throw KeychainError.encodingFailed }

        let query = baseQuery(secret, for: connectionID)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]

        let status = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
        if status == errSecSuccess { return }
        if status == errSecDuplicateItem {
            try update(data: data, secret, for: connectionID)
            return
        }
        throw KeychainError.unexpectedStatus(status)
    }

    func secret(_ secret: KeychainSecret, for connectionID: Connection.ID) throws -> String? {
        var query = baseQuery(secret, for: connectionID)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }
        guard let data = item as? Data, let value = String(data: data, encoding: .utf8) else { return nil }
        return value
    }

    func deleteSecrets(for connectionID: Connection.ID) throws {
        for secret in KeychainSecret.allCases {
            let status = SecItemDelete(baseQuery(secret, for: connectionID) as CFDictionary)
            guard status == errSecSuccess || status == errSecItemNotFound else {
                throw KeychainError.unexpectedStatus(status)
            }
        }
    }

    private func update(data: Data, _ secret: KeychainSecret, for connectionID: Connection.ID) throws {
        let attributes: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(baseQuery(secret, for: connectionID) as CFDictionary, attributes as CFDictionary)
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }
    }

    private func baseQuery(_ secret: KeychainSecret, for connectionID: Connection.ID) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account(secret, for: connectionID),
        ]
    }

    private func account(_ secret: KeychainSecret, for connectionID: Connection.ID) -> String {
        secret == .token ? connectionID.uuidString : "\(connectionID.uuidString).\(secret.rawValue)"
    }
}
