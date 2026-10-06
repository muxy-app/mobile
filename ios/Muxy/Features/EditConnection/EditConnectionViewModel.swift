import Foundation
import MuxyMobile
import Observation
import OSLog

@MainActor
@Observable
final class EditConnectionViewModel {
    private enum SaveError: Error {
        case missingCredentials
    }

    let connection: Connection
    var name: String
    var host: String
    var portText: String
    var username: String
    var authMethod: SSHAuthMethod
    var replacesCredentials = false {
        didSet {
            guard !replacesCredentials else { return }
            authMethod = connection.sshConfig?.authMethod ?? .password
            clearSecrets()
        }
    }
    var password = ""
    var privateKey = ""
    var passphrase = ""
    private(set) var isSaving = false
    private(set) var hasSaved = false
    private(set) var failure: String?

    private let store: ConnectionStore
    private let keychain: KeychainStore
    private let credentials: CredentialStore
    private let directory: ServerDirectory
    private let validator: ConnectionInputValidator

    init(
        connection: Connection,
        store: ConnectionStore,
        keychain: KeychainStore,
        credentials: CredentialStore,
        directory: ServerDirectory,
        validator: ConnectionInputValidator
    ) {
        self.connection = connection
        self.store = store
        self.keychain = keychain
        self.credentials = credentials
        self.directory = directory
        self.validator = validator
        name = connection.name
        host = connection.host
        portText = String(connection.port)
        username = connection.sshConfig?.username ?? ""
        authMethod = connection.sshConfig?.authMethod ?? .password
    }

    var canSave: Bool {
        guard !isSaving, !hasSaved, connection.id != DemoConnection.id else { return false }
        guard (try? validatedEndpoint().get()) != nil else { return false }
        guard connection.usesSSH else { return true }
        guard (try? validatedSSHConfig().get()) != nil else { return false }
        guard replacesCredentials else { return authMethod == connection.sshConfig?.authMethod }
        return (try? validatedSSHReplacement().get()) != nil
    }

    func save() -> Bool {
        guard canSave, case let .success(input) = validatedEndpoint() else { return false }
        isSaving = true
        failure = nil
        defer { isSaving = false }
        guard let current = store.load().first(where: { $0.id == connection.id }), current == connection else {
            failure = "This connection changed or was removed. Close this form and try again."
            return false
        }
        var updated = current
        updated.name = input.name
        updated.host = input.host
        updated.port = input.port
        let endpointChanged = updated.endpoint != current.endpoint
        if endpointChanged, updated.kind == .device {
            updated.serviceName = nil
            updated.discoverySource = .manual
        }
        do {
            try updateCredentials(for: &updated, endpointChanged: endpointChanged)
        } catch {
            Log.persistence.error("Couldn't securely save connection changes.")
            failure = "Couldn't securely save the changes. Your previous settings were kept. Try again."
            return false
        }
        store.upsert(updated)
        if let serverID = updated.serverRouteID {
            directory.credentialDidChange(for: serverID)
        }
        hasSaved = true
        clearSecrets()
        return true
    }

    private func updateCredentials(for updated: inout Connection, endpointChanged: Bool) throws {
        if updated.usesSSH {
            updated.sshConfig = try validatedSSHConfig().get()
            guard replacesCredentials else { return }
            let input = try validatedSSHReplacement().get()
            try keychain.replaceSSHCredentials(
                SSHCredentials(authMethod: input.authMethod, secret: input.secret, passphrase: input.passphrase),
                for: updated.id
            )
            return
        }
        switch updated.kind {
        case .device:
            return
        case .server:
            guard let serverID = updated.serverID,
                  let credential = try credentials.credential(serverId: serverID) else {
                throw SaveError.missingCredentials
            }
            try credentials.save(ServerCredential(
                serverId: credential.serverId,
                serverName: updated.name,
                hosts: endpointChanged ? [updated.host] : credential.hosts,
                port: endpointChanged ? UInt16(updated.port) : credential.port,
                fingerprint: credential.fingerprint,
                deviceId: credential.deviceId,
                token: credential.token
            ))
        case .ssh:
            return
        }
    }

    private func validatedEndpoint() -> Result<ValidatedConnectionInput, ConnectionInputError> {
        validator.validate(name: name, host: host, portText: portText)
    }

    private func validatedSSHConfig() -> Result<SSHConfig, ConnectionInputError> {
        validator.validateSSHConfig(username: username, authMethod: authMethod)
    }

    private func validatedSSHReplacement() -> Result<ValidatedSSHInput, ConnectionInputError> {
        validator.validateSSH(
            name: name,
            host: host,
            portText: portText,
            username: username,
            authMethod: authMethod,
            secret: authMethod == .password ? password : privateKey,
            passphrase: authMethod == .privateKey ? passphrase : ""
        )
    }

    private func clearSecrets() {
        password = ""
        privateKey = ""
        passphrase = ""
    }
}
