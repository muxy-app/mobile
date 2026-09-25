import Foundation
import MuxyMobile
import OSLog
import Security

nonisolated struct KeychainCredentialStore: CredentialStore {
    private let service: String

    init(service: String = "com.muxy.app.servers") {
        self.service = service
    }

    func all() throws -> [ServerCredential] {
        var query = serviceQuery()
        query[kSecReturnAttributes as String] = true
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitAll

        var items: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &items)
        if status == errSecItemNotFound { return [] }
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }
        let entries = items as? [[String: Any]] ?? []
        return entries
            .compactMap { $0[kSecValueData as String] as? Data }
            .compactMap(decode)
            .sorted { $0.serverName.localizedStandardCompare($1.serverName) == .orderedAscending }
    }

    func credential(serverId: String) throws -> ServerCredential? {
        var query = itemQuery(serverId: serverId)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw KeychainError.unexpectedStatus(status) }
        guard let data = item as? Data else { return nil }
        return decode(data)
    }

    func save(_ credential: ServerCredential) throws {
        let data = try JSONEncoder().encode(StoredCredential(credential))
        let query = itemQuery(serverId: credential.serverId)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]

        let status = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
        if status == errSecSuccess { return }
        guard status == errSecDuplicateItem else { throw KeychainError.unexpectedStatus(status) }
        let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        guard updateStatus == errSecSuccess else { throw KeychainError.unexpectedStatus(updateStatus) }
    }

    func delete(serverId: String) throws {
        let status = SecItemDelete(itemQuery(serverId: serverId) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.unexpectedStatus(status)
        }
    }

    private func decode(_ data: Data) -> ServerCredential? {
        do {
            return try JSONDecoder().decode(StoredCredential.self, from: data).credential
        } catch {
            Log.persistence.error("Skipping an unreadable paired computer: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    private func serviceQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
        ]
    }

    private func itemQuery(serverId: String) -> [String: Any] {
        serviceQuery().merging([kSecAttrAccount as String: serverId]) { _, new in new }
    }
}
