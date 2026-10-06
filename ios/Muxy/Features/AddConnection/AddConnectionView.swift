import SwiftUI

struct AddConnectionView: View {
    @State var viewModel: AddConnectionViewModel
    let pairingCode: String?
    let onAdded: (Connection) -> Void

    @State private var submissionTask: Task<Void, Never>?
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme
    @State private var scanError: String?
    @State private var hasAppliedPairingCode = false

    var body: some View {
        NavigationStack {
            ThemedForm {
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
            .tint(theme.accent)
            .screenTitle("Add Connection")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        submissionTask?.cancel()
                        dismiss()
                    }
                    .tint(theme.foreground)
                    .disabled(viewModel.isWorking && !viewModel.isRemoteSSH)
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
            .onDisappear {
                viewModel.stopDiscovery()
                submissionTask?.cancel()
            }
            .onChange(of: scenePhase) { _, phase in
                guard phase == .background else { return }
                submissionTask?.cancel()
            }
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
            Picker("Connection Method", selection: serverTransportBinding) {
                Text("Pairing Code").tag(ServerTransport.paired)
                Text("SSH").tag(ServerTransport.ssh)
            }
            .pickerStyle(.segmented)
        }
        .disabled(viewModel.isWorking)
        if viewModel.serverTransport == .ssh {
            sshSections
            Section {
                Text("Connect to a computer with Muxy installed using its SSH login. Muxy starts automatically; no pairing code is needed.")
                    .foregroundStyle(theme.secondaryForeground)
            }
        } else {
            serverPairingSections
        }
    }

    @ViewBuilder
    private var serverPairingSections: some View {
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
                ThemedSectionHeader("Computer")
            } footer: {
                Text("Only pair with a code shown on your own computer. A paired phone can do anything a terminal on that computer can.")
                    .foregroundStyle(theme.secondaryForeground)
            }

            Section {
                TextField("Device Name", text: deviceNameBinding)
                    .textInputAutocapitalization(.words)
                    .foregroundStyle(theme.foreground)
            } header: {
                ThemedSectionHeader("This Phone")
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
            ConnectionDetailsFields(name: $viewModel.name, host: $viewModel.host, portText: $viewModel.portText)
            TextField("Username", text: $viewModel.username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        } header: {
            ThemedSectionHeader("Server")
        }
        .foregroundStyle(theme.foreground)
        .disabled(viewModel.isWorking)

        Section {
            SSHAuthenticationFields(
                authMethod: $viewModel.authMethod,
                password: $viewModel.password,
                privateKey: $viewModel.privateKey,
                passphrase: $viewModel.passphrase
            )
        } header: {
            ThemedSectionHeader("Authentication")
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
            ThemedSectionHeader("Nearby")
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
            ConnectionDetailsFields(name: $viewModel.name, host: $viewModel.host, portText: $viewModel.portText)
        } header: {
            ThemedSectionHeader("Mac")
        }
        .foregroundStyle(theme.foreground)
        .disabled(viewModel.isWorking)
    }

    private var statusSection: some View {
        Section {
            StatusRow(status: viewModel.displayedStatus)
        }
    }

    private var submitButton: some View {
        Button("Add") {
            submissionTask = Task { await viewModel.submit(onAdded: onAdded) }
        }
        .disabled(!viewModel.canSubmit)
    }

    private var deviceNameBinding: Binding<String> {
        Binding(
            get: { viewModel.serverPairing.deviceName },
            set: { viewModel.serverPairing.deviceName = $0 }
        )
    }

    private var serverTransportBinding: Binding<ServerTransport> {
        Binding(
            get: { viewModel.serverTransport },
            set: { viewModel.selectServerTransport($0) }
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
                    .foregroundStyle(theme.yellow)
                Text("Approve this device on your Mac.")
                    .foregroundStyle(theme.foreground)
            }
        case .succeeded:
            Label("Connected", systemImage: "checkmark.circle.fill")
                .foregroundStyle(theme.green)
        case let .failed(message):
            Label(message, systemImage: "exclamationmark.triangle.fill")
                .foregroundStyle(theme.red)
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
