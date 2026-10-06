import SwiftUI

struct EditConnectionView: View {
    @State var viewModel: EditConnectionViewModel
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme

    var body: some View {
        NavigationStack {
            ThemedForm {
                detailsSection
                if viewModel.connection.usesSSH {
                    authenticationSection
                }
                if let failure = viewModel.failure {
                    Section {
                        Label(failure, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(theme.red)
                    }
                }
            }
            .disabled(viewModel.isSaving || viewModel.hasSaved)
            .tint(theme.accent)
            .screenTitle("Edit Connection")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .tint(theme.foreground)
                        .disabled(viewModel.isSaving || viewModel.hasSaved)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        guard viewModel.save() else { return }
                        onSaved()
                    }
                    .tint(theme.foreground)
                    .disabled(!viewModel.canSave)
                }
            }
        }
        .interactiveDismissDisabled(viewModel.isSaving || viewModel.hasSaved)
    }

    private var detailsSection: some View {
        Section {
            LabeledContent("Type", value: kindName)
            ConnectionDetailsFields(name: $viewModel.name, host: $viewModel.host, portText: $viewModel.portText)
            if viewModel.connection.usesSSH {
                TextField("Username", text: $viewModel.username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
        } header: {
            ThemedSectionHeader("Connection")
        } footer: {
            Text("Saving updates this connection without connecting or pairing again. Use a port from 1 to 65535. Existing trust is preserved when the address changes.")
                .foregroundStyle(theme.secondaryForeground)
        }
        .foregroundStyle(theme.foreground)
    }

    private var authenticationSection: some View {
        Section {
            Toggle("Replace saved credentials", isOn: $viewModel.replacesCredentials)
            SSHAuthenticationFields(
                authMethod: $viewModel.authMethod,
                password: $viewModel.password,
                privateKey: $viewModel.privateKey,
                passphrase: $viewModel.passphrase,
                showsSecrets: viewModel.replacesCredentials,
                allowsMethodChange: viewModel.replacesCredentials
            )
        } header: {
            ThemedSectionHeader("Authentication")
        } footer: {
            Text("Saved secrets are never displayed. Leave replacement off to keep them, including when changing the username. Turn it on to change authentication and enter a new password or private key. An empty passphrase means no passphrase for the replacement key.")
                .foregroundStyle(theme.secondaryForeground)
        }
        .foregroundStyle(theme.foreground)
    }

    private var kindName: String {
        switch viewModel.connection.kind {
        case .device: "Muxy 1"
        case .server: viewModel.connection.serverTransport == .ssh ? "Muxy 2 over SSH" : "Muxy 2"
        case .ssh: "SSH"
        }
    }
}
