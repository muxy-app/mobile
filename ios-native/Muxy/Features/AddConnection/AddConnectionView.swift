import SwiftUI

struct AddConnectionView: View {
    @State var viewModel: AddConnectionViewModel
    let pairingCode: String?
    let onAdded: (Connection) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme
    @State private var scanError: String?
    @State private var hasAppliedPairingCode = false

    var body: some View {
        NavigationStack {
            Form {
                kindSection
                switch viewModel.kind {
                case .device:
                    deviceSections
                case .server:
                    serverSections
                case .ssh:
                    sshSections
                }
                if viewModel.displayedStatus != .idle {
                    statusSection
                }
            }
            .themedSurface()
            .tint(theme.accent)
            .screenTitle("Add Connection")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .tint(theme.foreground)
                        .disabled(viewModel.isWorking)
                }
                ToolbarItem(placement: .confirmationAction) {
                    submitButton
                        .tint(theme.foreground)
                }
            }
            .sheet(isPresented: $viewModel.isShowingScanner) {
                QRScannerView(
                    onScan: handleScan,
                    onCancel: { viewModel.isShowingScanner = false }
                )
            }
            .alert("Invalid QR Code", isPresented: scanErrorBinding) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(scanError ?? "")
            }
            .task {
                applyInitialPairingCode()
                viewModel.startDiscovery()
            }
            .onDisappear { viewModel.stopDiscovery() }
        }
        .interactiveDismissDisabled(viewModel.isWorking)
    }

    private var kindSection: some View {
        Section {
            Picker("Type", selection: kindBinding) {
                Text("Muxy 1").tag(ConnectionKind.device)
                Text("Muxy 2").tag(ConnectionKind.server)
                Text("SSH").tag(ConnectionKind.ssh)
            }
            .pickerStyle(.segmented)
            .disabled(viewModel.isWorking)
        }
    }

    @ViewBuilder
    private var deviceSections: some View {
        nearbySection
        scanSection
        manualSection
    }

    @ViewBuilder
    private var serverSections: some View {
        Section {
            Button {
                viewModel.isShowingScanner = true
            } label: {
                Label("Scan QR Code", systemImage: "qrcode.viewfinder")
                    .foregroundStyle(theme.foreground)
            }
            .buttonStyle(.plain)

            PasteButton(payloadType: String.self) { links in
                guard let link = links.first else { return }
                viewModel.serverPairing.receive(link: link, source: .manual)
            }
            .tint(theme.accent)
        }
        .disabled(viewModel.isWorking)

        if let target = viewModel.serverPairing.target {
            Section {
                LabeledContent("Address", value: target.address)
                    .foregroundStyle(theme.foreground)
            } header: {
                sectionHeader("Computer")
            } footer: {
                Text("Only pair with a code shown on your own computer. A paired phone can do anything a terminal on that computer can.")
                    .foregroundStyle(theme.secondaryForeground)
            }

            Section {
                TextField("Device Name", text: deviceNameBinding)
                    .textInputAutocapitalization(.words)
                    .foregroundStyle(theme.foreground)
            } header: {
                sectionHeader("This Phone")
            } footer: {
                Text("Your computer lists this phone under this name in Settings → Mobile.")
                    .foregroundStyle(theme.secondaryForeground)
            }
            .disabled(viewModel.isWorking)
        }
    }

    @ViewBuilder
    private var sshSections: some View {
        Section {
            TextField("Name", text: $viewModel.name)
                .textInputAutocapitalization(.words)
            TextField("Host", text: $viewModel.host)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
            TextField("Port", text: $viewModel.portText)
                .keyboardType(.numberPad)
            TextField("Username", text: $viewModel.username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        } header: {
            sectionHeader("Server")
        }
        .foregroundStyle(theme.foreground)
        .disabled(viewModel.isWorking)

        Section {
            Picker("Authentication", selection: $viewModel.authMethod) {
                Text("Password").tag(SSHAuthMethod.password)
                Text("Private Key").tag(SSHAuthMethod.privateKey)
            }
            if viewModel.authMethod == .password {
                SecureField("Password", text: $viewModel.password)
            } else {
                TextField("Private Key", text: $viewModel.privateKey, axis: .vertical)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .lineLimit(4...8)
                SecureField("Passphrase (optional)", text: $viewModel.passphrase)
            }
        } header: {
            sectionHeader("Authentication")
        }
        .foregroundStyle(theme.foreground)
        .disabled(viewModel.isWorking)
    }

    private var nearbySection: some View {
        Section {
            if viewModel.discoveredServices.isEmpty {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Searching for Macs…")
                        .foregroundStyle(theme.secondaryForeground)
                }
            } else {
                ForEach(viewModel.discoveredServices) { service in
                    Button {
                        viewModel.applyDiscovered(service)
                    } label: {
                        discoveredRow(service)
                    }
                    .buttonStyle(.plain)
                }
            }
        } header: {
            sectionHeader("Nearby")
        }
    }

    private func discoveredRow(_ service: DiscoveredService) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "desktopcomputer")
                .foregroundStyle(theme.foreground)
            VStack(alignment: .leading) {
                Text(service.name)
                    .foregroundStyle(theme.foreground)
                Text("\(service.host):\(String(service.port))")
                    .font(.caption)
                    .foregroundStyle(theme.secondaryForeground)
            }
        }
    }

    private var scanSection: some View {
        Section {
            Button {
                viewModel.isShowingScanner = true
            } label: {
                Label("Scan QR Code", systemImage: "qrcode.viewfinder")
                    .foregroundStyle(theme.foreground)
            }
            .buttonStyle(.plain)
        }
    }

    private var manualSection: some View {
        Section {
            TextField("Name", text: $viewModel.name)
                .textInputAutocapitalization(.words)
            TextField("Host", text: $viewModel.host)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
            TextField("Port", text: $viewModel.portText)
                .keyboardType(.numberPad)
        } header: {
            sectionHeader("Mac")
        }
        .foregroundStyle(theme.foreground)
        .disabled(viewModel.isWorking)
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .foregroundStyle(theme.secondaryForeground)
    }

    private var statusSection: some View {
        Section {
            StatusRow(status: viewModel.displayedStatus)
        }
    }

    private var submitButton: some View {
        Button("Add") {
            Task { await viewModel.submit(onAdded: onAdded) }
        }
        .disabled(!viewModel.canSubmit)
    }

    private var deviceNameBinding: Binding<String> {
        Binding(
            get: { viewModel.serverPairing.deviceName },
            set: { viewModel.serverPairing.deviceName = $0 }
        )
    }

    private var kindBinding: Binding<ConnectionKind> {
        Binding(
            get: { viewModel.kind },
            set: { viewModel.selectKind($0) }
        )
    }

    private func handleScan(_ code: String) {
        guard !viewModel.applyPairingCode(code, source: .qr) else { return }
        scanError = "That code isn't a Muxy pairing code."
    }

    private func applyInitialPairingCode() {
        guard !hasAppliedPairingCode, let pairingCode else { return }
        hasAppliedPairingCode = true
        handleScan(pairingCode)
    }

    private var scanErrorBinding: Binding<Bool> {
        Binding(
            get: { scanError != nil },
            set: { if !$0 { scanError = nil } }
        )
    }
}

private struct StatusRow: View {
    let status: AddConnectionViewModel.Status

    @Environment(\.appTheme) private var theme

    var body: some View {
        switch status {
        case .idle:
            EmptyView()
        case .connecting:
            progress("Connecting…")
        case .authenticating:
            progress("Authenticating…")
        case .awaitingApproval:
            HStack(spacing: 8) {
                Image(systemName: "hand.raised.fill")
                    .foregroundStyle(.orange)
                Text("Approve this device on your Mac.")
                    .foregroundStyle(theme.foreground)
            }
        case .succeeded:
            Label("Connected", systemImage: "checkmark.circle.fill")
                .foregroundStyle(.green)
        case let .failed(message):
            Label(message, systemImage: "exclamationmark.triangle.fill")
                .foregroundStyle(.red)
        }
    }

    private func progress(_ text: String) -> some View {
        HStack(spacing: 8) {
            ProgressView()
            Text(text)
                .foregroundStyle(theme.foreground)
        }
    }
}
