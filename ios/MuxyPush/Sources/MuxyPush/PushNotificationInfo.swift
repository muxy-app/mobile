import Foundation

public enum PushNotificationInfo {
    private static let container = "muxy"
    private static let versionField = "v"
    private static let payloadField = "payload"
    private static let kindField = "kind"
    private static let supportedVersion = 1

    public static func payload(in userInfo: [AnyHashable: Any]) -> String? {
        guard let muxy = muxy(in: userInfo) else { return nil }
        return muxy[payloadField] as? String
    }

    public static func kind(in userInfo: [AnyHashable: Any]) -> String? {
        muxy(in: userInfo)?[kindField] as? String
    }

    public static func isMuxy(_ userInfo: [AnyHashable: Any]) -> Bool {
        muxy(in: userInfo) != nil
    }

    public static func target(in userInfo: [AnyHashable: Any], keys: [PushKey]) -> PushTarget? {
        guard let payload = payload(in: userInfo) else { return nil }
        return PushOpener.open(payload, keys: keys)?.target
    }

    private static func muxy(in userInfo: [AnyHashable: Any]) -> [String: Any]? {
        guard let muxy = userInfo[container] as? [String: Any] else { return nil }
        guard (muxy[versionField] as? Int) == supportedVersion else { return nil }
        return muxy
    }
}
