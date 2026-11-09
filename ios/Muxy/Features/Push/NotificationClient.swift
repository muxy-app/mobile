import Foundation
import UIKit
import UserNotifications

nonisolated enum PushAuthorization: Equatable, Sendable {
    case notDetermined
    case denied
    case allowed

    init(_ status: UNAuthorizationStatus) {
        switch status {
        case .notDetermined:
            self = .notDetermined
        case .authorized, .provisional, .ephemeral:
            self = .allowed
        case .denied:
            self = .denied
        @unknown default:
            self = .denied
        }
    }
}

@MainActor
protocol NotificationClient: AnyObject {
    func authorization() async -> PushAuthorization
    func requestAuthorization() async throws -> Bool
    func registerForRemoteNotifications()
    func removeDeliveredNotifications(withIdentifiers identifiers: [String])
}

@MainActor
final class SystemNotificationClient: NotificationClient {
    private var center: UNUserNotificationCenter {
        .current()
    }

    func authorization() async -> PushAuthorization {
        PushAuthorization(await center.notificationSettings().authorizationStatus)
    }

    func requestAuthorization() async throws -> Bool {
        try await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    func registerForRemoteNotifications() {
        UIApplication.shared.registerForRemoteNotifications()
    }

    func removeDeliveredNotifications(withIdentifiers identifiers: [String]) {
        center.removeDeliveredNotifications(withIdentifiers: identifiers)
    }
}
