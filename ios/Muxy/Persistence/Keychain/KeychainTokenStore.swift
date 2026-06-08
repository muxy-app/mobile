import Foundation
import Security

struct KeychainTokenStore: KeychainStore {
    private let service: String

    init(service: String = "com.muxy.app.tokens") {
        self.service = service
    }

    func setToken(_ token: String, for deviceID: Device.ID) throws {
        guard let data = token.data(using: .utf8) else { throw KeychainError.encodingFailed }

        let query = baseQuery(for: deviceID)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]

        let status = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
        if status == errSecSuccess { return }
        if status == errSecDuplicateItem {
            try update(data: data, for: deviceID)
            return
        }
        throw KeychainError.unexpectedStatus(status)
    }

    func token(for deviceID: Device.ID) throws -> String? {
        var query = baseQuery(for: deviceID)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }
        guard let data = item as? Data, let token = String(data: data, encoding: .utf8) else { return nil }
        return token
    }

    func deleteToken(for deviceID: Device.ID) throws {
        let status = SecItemDelete(baseQuery(for: deviceID) as CFDictionary)
        if status == errSecSuccess || status == errSecItemNotFound { return }
        throw KeychainError.unexpectedStatus(status)
    }

    private func update(data: Data, for deviceID: Device.ID) throws {
        let attributes: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(baseQuery(for: deviceID) as CFDictionary, attributes as CFDictionary)
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }
    }

    private func baseQuery(for deviceID: Device.ID) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: deviceID.uuidString,
        ]
    }
}
