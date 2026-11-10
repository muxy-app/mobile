import Foundation

public enum PushKind: String, Sendable {
    case attention
    case completed

    public var fallback: PushAlert {
        switch self {
        case .attention: PushAlert(title: "Muxy", body: "An agent needs your attention")
        case .completed: PushAlert(title: "Muxy", body: "An agent finished working")
        }
    }
}

public struct PushAlert: Equatable, Sendable {
    public let title: String
    public let subtitle: String?
    public let body: String
    public let threadID: String?

    public init(title: String, subtitle: String? = nil, body: String, threadID: String? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.body = body
        self.threadID = threadID
    }
}

public enum PushDisplay {
    public static func alert(for payload: String?, keys: [PushKey], servers: [PushServer]) -> PushAlert? {
        guard let payload, let message = PushOpener.open(payload, keys: keys) else { return nil }
        guard let key = PushOpener.key(for: message, in: keys) else { return nil }
        return PushAlert(
            title: message.title,
            subtitle: subtitle(for: message.serverId, among: servers),
            body: message.body,
            threadID: key.threadID(projectId: message.projectId)
        )
    }

    public static func alert(for payload: String?, kind: String?, fallback: PushAlert?, keys: [PushKey], servers: [PushServer]) -> PushAlert {
        if let decrypted = alert(for: payload, keys: keys, servers: servers) { return decrypted }
        if let fallback { return fallback }
        return (kind.flatMap(PushKind.init(rawValue:)) ?? .attention).fallback
    }

    private static func subtitle(for serverId: String, among servers: [PushServer]) -> String? {
        guard servers.count > 1 else { return nil }
        return servers.first { $0.serverId == serverId }?.name
    }
}
