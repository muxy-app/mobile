import Foundation
import UIKit

struct ConnectionIssue {
    let message: String
    let technicalDetails: String
}

struct ConnectionDiagnostics {
    private(set) var log: [String] = []

    mutating func record(_ message: String) {
        log.append("\(Self.timestampString(Date())) \(message)")
        if log.count > 120 {
            log.removeFirst(log.count - 120)
        }
    }

    mutating func clear() {
        log.removeAll()
    }

    // swiftlint:disable:next function_parameter_count
    func makeIssue(
        message: String,
        operation: String,
        stateSummary: String,
        deviceName: String?,
        host: String?,
        port: UInt16?,
        requestMethod: MuxyMethod?,
        requestID: String?,
        response: MuxyResponse?,
        underlyingError: Error?,
        notes: [String]
    ) -> ConnectionIssue {
        var lines = [
            "Summary: \(message)",
            "Operation: \(operation)",
            "Timestamp: \(Self.timestampString(Date()))",
            "Connection state: \(stateSummary)",
        ]

        if let deviceName {
            lines.append("Device: \(deviceName)")
        }

        if let host, let port {
            lines.append("Target: \(host):\(port)")
        }

        if let requestMethod {
            lines.append("Request: \(requestMethod.rawValue)")
        }

        if let requestID {
            lines.append("Request ID: \(requestID)")
        }

        if let responseError = response?.error {
            lines.append("Response error: \(responseError.code) \(responseError.message)")
        }

        if let response, response.error == nil {
            lines.append("Response result: \(Self.resultSummary(response.result))")
        }

        if let underlyingError {
            lines.append("Underlying error: \(Self.inlineErrorDescription(underlyingError))")
        }

        if let appVersion = Self.appVersionString {
            lines.append("App version: \(appVersion)")
        }

        lines.append("OS: \(UIDevice.current.systemName) \(UIDevice.current.systemVersion)")
        lines.append(contentsOf: notes)

        if !log.isEmpty {
            lines.append("")
            lines.append("Recent connection log:")
            lines.append(contentsOf: log.suffix(25).map { "- \($0)" })
        }

        return ConnectionIssue(message: message, technicalDetails: lines.joined(separator: "\n"))
    }

    static func responseSummary(_ response: MuxyResponse) -> String {
        if let error = response.error {
            return "error \(error.code) \(error.message)"
        }
        return "result \(resultSummary(response.result))"
    }

    static func resultSummary(_ result: MuxyResult?) -> String {
        guard let result else { return "nil" }

        switch result {
        case let .projects(list):
            return "projects(\(list.count))"
        case let .worktrees(list):
            return "worktrees(\(list.count))"
        case .workspace:
            return "workspace"
        case .tab:
            return "tab"
        case .terminalContent:
            return "terminalContent"
        case .terminalCells:
            return "terminalCells"
        case .deviceInfo:
            return "deviceInfo"
        case .pairing:
            return "pairing"
        case .paneOwner:
            return "paneOwner"
        case .vcsStatus:
            return "vcsStatus"
        case .vcsBranches:
            return "vcsBranches"
        case .vcsPRCreated:
            return "vcsPRCreated"
        case .projectLogo:
            return "projectLogo"
        case let .notifications(list):
            return "notifications(\(list.count))"
        case .ok:
            return "ok"
        }
    }

    static func inlineErrorDescription(_ error: Error) -> String {
        let nsError = error as NSError
        var parts = ["\(nsError.domain) \(nsError.code): \(error.localizedDescription)"]

        if let reason = nsError.localizedFailureReason {
            parts.append(reason)
        }

        if let suggestion = nsError.localizedRecoverySuggestion {
            parts.append(suggestion)
        }

        return parts.joined(separator: " | ")
    }

    private static func timestampString(_ date: Date) -> String {
        diagnosticsFormatter.string(from: date)
    }

    nonisolated(unsafe) private static let diagnosticsFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static var appVersionString: String? {
        let shortVersion = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String

        switch (shortVersion, build) {
        case let (shortVersion?, build?) where shortVersion != build:
            return "\(shortVersion) (\(build))"
        case let (shortVersion?, _):
            return shortVersion
        case let (_, build?):
            return build
        default:
            return nil
        }
    }
}
