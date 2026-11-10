import Foundation
import Security

public enum PushKeyStoreError: Error, Equatable, Sendable {
    case unexpectedStatus(OSStatus)
}

public protocol PushKeyStore: Sendable {
    func keys() throws -> [PushKey]
    func key(serverId: String) throws -> PushKey?
    func save(_ key: PushKey) throws
    func delete(serverId: String) throws
}

public struct KeychainPushKeyStore: PushKeyStore {
    private let service: String
    private let accessGroup: String?

    public init(accessGroup: String?, service: String = "com.muxy.app.push-keys") {
        self.accessGroup = accessGroup
        self.service = service
    }

    public func keys() throws -> [PushKey] {
        var query = baseQuery()
        query[kSecReturnAttributes as String] = true
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitAll

        var items: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &items)
        if status == errSecItemNotFound { return [] }
        guard status == errSecSuccess else { throw PushKeyStoreError.unexpectedStatus(status) }
        let entries = items as? [[String: Any]] ?? []
        return entries.compactMap { entry in
            guard let serverId = entry[kSecAttrAccount as String] as? String,
                  let bytes = entry[kSecValueData as String] as? Data else { return nil }
            return PushKey(serverId: serverId, bytes: bytes)
        }
    }

    public func key(serverId: String) throws -> PushKey? {
        var query = itemQuery(serverId: serverId)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw PushKeyStoreError.unexpectedStatus(status) }
        guard let bytes = item as? Data else { return nil }
        return PushKey(serverId: serverId, bytes: bytes)
    }

    public func save(_ key: PushKey) throws {
        let query = itemQuery(serverId: key.serverId)
        let attributes: [String: Any] = [
            kSecValueData as String: key.bytes,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let status = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
        if status == errSecSuccess { return }
        guard status == errSecDuplicateItem else { throw PushKeyStoreError.unexpectedStatus(status) }
        let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        guard updateStatus == errSecSuccess else { throw PushKeyStoreError.unexpectedStatus(updateStatus) }
    }

    public func delete(serverId: String) throws {
        let status = SecItemDelete(itemQuery(serverId: serverId) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw PushKeyStoreError.unexpectedStatus(status)
        }
    }

    private func baseQuery() -> [String: Any] {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
        ]
        if let accessGroup {
            query[kSecAttrAccessGroup as String] = accessGroup
        }
        return query
    }

    private func itemQuery(serverId: String) -> [String: Any] {
        baseQuery().merging([kSecAttrAccount as String: serverId]) { _, new in new }
    }
}
