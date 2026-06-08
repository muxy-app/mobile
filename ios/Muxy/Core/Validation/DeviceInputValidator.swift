import Foundation

enum DeviceInputError: Error, Equatable, Sendable {
    case emptyName
    case emptyHost
    case invalidHost
    case missingPort
    case invalidPort
}

struct ValidatedDeviceInput: Equatable, Sendable {
    let name: String
    let host: String
    let port: Int
}

struct DeviceInputValidator: Sendable {
    func validate(name: String, host: String, portText: String) -> Result<ValidatedDeviceInput, DeviceInputError> {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return .failure(.emptyName) }

        let trimmedHost = host.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedHost.isEmpty else { return .failure(.emptyHost) }
        guard isValidHost(trimmedHost) else { return .failure(.invalidHost) }

        let trimmedPort = portText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedPort.isEmpty else { return .failure(.missingPort) }
        guard let port = Int(trimmedPort), (1...65535).contains(port) else { return .failure(.invalidPort) }

        return .success(ValidatedDeviceInput(name: trimmedName, host: trimmedHost, port: port))
    }

    func isValidHost(_ host: String) -> Bool {
        guard !host.isEmpty, !host.contains(" ") else { return false }
        let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-:")
        return host.unicodeScalars.allSatisfy { allowed.contains($0) }
    }
}
