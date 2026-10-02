import Foundation

nonisolated enum ConnectionInputError: Error, Equatable, Sendable {
    case emptyName
    case emptyHost
    case invalidHost
    case missingPort
    case invalidPort
    case emptyUsername
    case emptyPassword
    case emptyPrivateKey
}

struct ValidatedConnectionInput: Equatable, Sendable {
    let name: String
    let host: String
    let port: Int
}

struct ValidatedSSHInput: Equatable, Sendable {
    let name: String
    let host: String
    let port: Int
    let username: String
    let authMethod: SSHAuthMethod
    let secret: String
    let passphrase: String?
}

struct ConnectionInputValidator: Sendable {
    func validate(name: String, host: String, portText: String) -> Result<ValidatedConnectionInput, ConnectionInputError> {
        guard let endpoint = validateEndpoint(name: name, host: host, portText: portText) else {
            return endpointFailure(name: name, host: host, portText: portText)
        }
        return .success(ValidatedConnectionInput(name: endpoint.name, host: endpoint.host, port: endpoint.port))
    }

    func validateSSH(
        name: String,
        host: String,
        portText: String,
        username: String,
        authMethod: SSHAuthMethod,
        secret: String,
        passphrase: String
    ) -> Result<ValidatedSSHInput, ConnectionInputError> {
        guard let endpoint = validateEndpoint(name: name, host: host, portText: portText) else {
            return .failure(endpointError(name: name, host: host, portText: portText))
        }

        let trimmedUsername = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedUsername.isEmpty else { return .failure(.emptyUsername) }

        switch authMethod {
        case .password:
            guard !secret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return .failure(.emptyPassword) }
        case .privateKey:
            guard !secret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return .failure(.emptyPrivateKey) }
        }

        let trimmedPassphrase = passphrase.trimmingCharacters(in: .whitespacesAndNewlines)
        return .success(
            ValidatedSSHInput(
                name: endpoint.name,
                host: endpoint.host,
                port: endpoint.port,
                username: trimmedUsername,
                authMethod: authMethod,
                secret: secret,
                passphrase: trimmedPassphrase.isEmpty ? nil : passphrase
            )
        )
    }

    func isValidHost(_ host: String) -> Bool {
        guard !host.isEmpty, !host.contains(" ") else { return false }
        let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-:")
        return host.unicodeScalars.allSatisfy { allowed.contains($0) }
    }

    private func validateEndpoint(name: String, host: String, portText: String) -> ValidatedConnectionInput? {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return nil }

        let trimmedHost = host.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedHost.isEmpty, isValidHost(trimmedHost) else { return nil }

        let trimmedPort = portText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedPort.isEmpty, let port = Int(trimmedPort), (1...65535).contains(port) else { return nil }

        return ValidatedConnectionInput(name: trimmedName, host: trimmedHost, port: port)
    }

    private func endpointFailure<T>(name: String, host: String, portText: String) -> Result<T, ConnectionInputError> {
        .failure(endpointError(name: name, host: host, portText: portText))
    }

    private func endpointError(name: String, host: String, portText: String) -> ConnectionInputError {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return .emptyName }

        let trimmedHost = host.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedHost.isEmpty else { return .emptyHost }
        guard isValidHost(trimmedHost) else { return .invalidHost }

        let trimmedPort = portText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedPort.isEmpty else { return .missingPort }
        return .invalidPort
    }
}
