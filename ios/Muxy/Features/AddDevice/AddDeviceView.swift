import SwiftUI

struct AddDeviceView: View {
    @State var viewModel: AddDeviceViewModel
    let onPaired: (Device) -> Void

    @Environment(\.dismiss) private var dismiss
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
            .navigationTitle("Add Device")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .disabled(viewModel.isPairing)
                }
                ToolbarItem(placement: .confirmationAction) {
                    submitButton
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
        Section("Nearby") {
            if viewModel.discoveredServices.isEmpty {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Searching for Macs…")
                        .foregroundStyle(.secondary)
                }
            } else {
                ForEach(viewModel.discoveredServices) { service in
                    Button {
                        viewModel.applyDiscovered(service)
                    } label: {
                        discoveredRow(service)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.primary)
                }
            }
        }
    }

    private func discoveredRow(_ service: DiscoveredService) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "desktopcomputer")
                .foregroundStyle(.primary)
            VStack(alignment: .leading) {
                Text(service.name)
                    .foregroundStyle(.primary)
                Text("\(service.host):\(String(service.port))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var scanSection: some View {
        Section {
            Button {
                viewModel.isShowingScanner = true
            } label: {
                Label("Scan QR Code", systemImage: "qrcode.viewfinder")
            }
            .buttonStyle(.plain)
            .foregroundStyle(.primary)
        }
    }

    private var manualSection: some View {
        Section("Device") {
            TextField("Name", text: $viewModel.name)
                .textInputAutocapitalization(.words)
            TextField("Host", text: $viewModel.host)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
            TextField("Port", text: $viewModel.portText)
                .keyboardType(.numberPad)
        }
        .disabled(viewModel.isPairing)
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
