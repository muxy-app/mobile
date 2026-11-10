import MuxyPush
import OSLog
import UserNotifications

final class NotificationService: UNNotificationServiceExtension {
    private static let log = Logger(subsystem: "com.muxy.app.NotificationService", category: "push")

    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var original: UNNotificationContent?

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        self.contentHandler = contentHandler
        original = request.content
        deliver(Self.decorated(request.content))
    }

    override func serviceExtensionTimeWillExpire() {
        guard let original else { return }
        deliver(original)
    }

    private func deliver(_ content: UNNotificationContent) {
        guard let contentHandler else { return }
        self.contentHandler = nil
        contentHandler(content)
    }

    private static func decorated(_ content: UNNotificationContent) -> UNNotificationContent {
        guard let payload = PushNotificationInfo.payload(in: content.userInfo) else { return content }
        let keyring = PushKeyring.shared()
        let keys: [PushKey]
        do {
            keys = try keyring.activeKeys()
        } catch {
            log.error("Reading push keys failed: \(String(describing: error), privacy: .public)")
            return content
        }
        guard let alert = PushDisplay.alert(for: payload, keys: keys, servers: keyring.servers.servers()),
              let decorated = content.mutableCopy() as? UNMutableNotificationContent else { return content }
        decorated.title = alert.title
        decorated.body = alert.body
        if let subtitle = alert.subtitle {
            decorated.subtitle = subtitle
        }
        if let threadID = alert.threadID {
            decorated.threadIdentifier = threadID
        }
        return decorated
    }
}
