import SwiftUI

struct AddDeviceView: View {
    @State var viewModel: AddDeviceViewModel
    let onPaired: (Device) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.appTheme) private var theme
    @State private var scanError: String?

    var body: some View {
        NavigationStack {
            Form {
                nearbySection
                scanSection
                manualSection
                if viewModel.status != .idle {
                    statusSection
                }
            }
            .themedSurface()
            .tint(theme.accent)
            .navigationTitle("Add Device")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .tint(theme.foreground)
                        .disabled(viewModel.isPairing)
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
            .task { viewModel.startDiscovery() }
            .onDisappear { viewModel.stopDiscovery() }
        }
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
            sectionHeader("Device")
        }
        .foregroundStyle(theme.foreground)
        .disabled(viewModel.isPairing)
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .foregroundStyle(theme.secondaryForeground)
    }

    private var statusSection: some View {
        Section {
            StatusRow(status: viewModel.status)
        }
    }

    private var submitButton: some View {
        Button("Add") {
            Task { await viewModel.submit(onPaired: onPaired) }
        }
        .disabled(!viewModel.canSubmit)
    }

    private func handleScan(_ result: Result<PairingURI, PairingURIError>) {
        switch result {
        case let .success(uri):
            viewModel.applyScan(uri)
        case .failure:
            viewModel.isShowingScanner = false
            scanError = "That code isn't a Muxy pairing code."
        }
    }

    private var scanErrorBinding: Binding<Bool> {
        Binding(
            get: { scanError != nil },
            set: { if !$0 { scanError = nil } }
        )
    }
}

private struct StatusRow: View {
    let status: PairingStatus

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
        case .paired:
            Label("Paired", systemImage: "checkmark.circle.fill")
                .foregroundStyle(.green)
        case let .failed(error):
            Label(message(for: error), systemImage: "exclamationmark.triangle.fill")
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

    private func message(for error: PairingError) -> String {
        switch error {
        case .connectionFailed:
            return "Couldn't connect. Check the host and port."
        case .approvalDenied:
            return "The Mac denied this device."
        case .approvalTimedOut:
            return "Approval timed out. Try again."
        case .wrongToken:
            return "This device's credentials are invalid. Remove it and add it again."
        case .invalidResponse:
            return "The Mac sent an unexpected response."
        case let .server(code, message):
            return "Server error \(code): \(message)"
        }
    }
}
