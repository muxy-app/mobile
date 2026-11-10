import Foundation

public struct PushKeyring: Sendable {
    public let keys: any PushKeyStore
    public let servers: PushServerDirectory

    public init(keys: any PushKeyStore, servers: PushServerDirectory) {
        self.keys = keys
        self.servers = servers
    }

    public static func shared(bundle: Bundle = .main) -> PushKeyring {
        let configuration = PushSharedConfiguration(bundle: bundle)
        return PushKeyring(
            keys: KeychainPushKeyStore(accessGroup: configuration.keychainGroup),
            servers: PushServerDirectory(defaults: configuration.appGroupDefaults)
        )
    }

    public func activeKeys() throws -> [PushKey] {
        try keys.keys().filter { servers.isEnabled($0.serverId) }
    }
}

public struct PushSharedConfiguration: Sendable {
    public let keychainGroup: String?
    public let appGroup: String?

    public init(bundle: Bundle) {
        keychainGroup = Self.value("MuxyPushKeychainGroup", in: bundle)
        appGroup = Self.value("MuxyAppGroup", in: bundle)
    }

    public var appGroupDefaults: UserDefaults {
        appGroup.flatMap(UserDefaults.init(suiteName:)) ?? .standard
    }

    private static func value(_ key: String, in bundle: Bundle) -> String? {
        guard let value = bundle.object(forInfoDictionaryKey: key) as? String, !value.isEmpty, !value.hasPrefix("$(") else { return nil }
        return value
    }
}
