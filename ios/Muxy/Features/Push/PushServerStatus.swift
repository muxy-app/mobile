import Foundation
import MuxyMobile

nonisolated enum PushServerStatus: String, Sendable {
    case active
    case pending
    case refused
    case disabled
    case unsupported
    case other

    init(_ status: PushStatus) {
        switch status {
        case .active: self = .active
        case .pending: self = .pending
        case .refused: self = .refused
        case .disabled: self = .disabled
        case .other: self = .other
        }
    }
}

nonisolated enum PushServerSummary {
    static func text(
        authorization: PushAuthorization,
        isEnabled: Bool,
        status: PushServerStatus?,
        serverName: String
    ) -> String {
        guard isEnabled else { return "Off" }
        if status == .unsupported { return "Update Muxy on \(serverName) to get notifications." }
        if status == .disabled { return "Notifications are turned off on \(serverName)." }
        switch authorization {
        case .denied:
            return "Notifications are off for Muxy in iOS Settings."
        case .notDetermined:
            return "Allow notifications to hear from \(serverName)."
        case .allowed:
            return allowedText(status: status, serverName: serverName)
        }
    }

    private static func allowedText(status: PushServerStatus?, serverName: String) -> String {
        switch status {
        case .active:
            return "On"
        case .pending:
            return "On. \(serverName) is still reaching muxy.app."
        case .refused:
            return "Apple refused this device's notification token. Muxy will try again."
        case .other:
            return "On"
        case .disabled, .unsupported, nil:
            return "Turns on the next time Muxy connects to \(serverName)."
        }
    }
}
