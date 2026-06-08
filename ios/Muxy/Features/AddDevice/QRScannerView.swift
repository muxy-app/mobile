import AVFoundation
import SwiftUI

struct QRScannerView: View {
    let onScan: (Result<PairingURI, PairingURIError>) -> Void
    let onCancel: () -> Void

    @State private var authorization = AVCaptureDevice.authorizationStatus(for: .video)

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Scan QR Code")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel", action: onCancel)
                    }
                }
        }
        .task { await requestAccessIfNeeded() }
    }

    @ViewBuilder
    private var content: some View {
        switch authorization {
        case .authorized:
            ScannerRepresentable { code in
                onScan(parse(code))
            }
            .ignoresSafeArea(edges: .bottom)
        case .notDetermined:
            ProgressView()
        default:
            cameraDeniedView
        }
    }

    private var cameraDeniedView: some View {
        ContentUnavailableView {
            Label("Camera Access Needed", systemImage: "camera.fill")
        } description: {
            Text("Allow camera access in Settings to scan the pairing code on your Mac.")
        } actions: {
            Button("Open Settings") {
                guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                UIApplication.shared.open(url)
            }
            .buttonStyle(.borderedProminent)
        }
    }

    private func parse(_ code: String) -> Result<PairingURI, PairingURIError> {
        do {
            return .success(try PairingURI.parse(code))
        } catch {
            return .failure(error)
        }
    }

    private func requestAccessIfNeeded() async {
        guard authorization == .notDetermined else { return }
        _ = await AVCaptureDevice.requestAccess(for: .video)
        authorization = AVCaptureDevice.authorizationStatus(for: .video)
    }
}

private struct ScannerRepresentable: UIViewControllerRepresentable {
    let onCode: (String) -> Void

    func makeUIViewController(context: Context) -> QRScannerController {
        let controller = QRScannerController()
        controller.onCode = onCode
        return controller
    }

    func updateUIViewController(_ controller: QRScannerController, context: Context) {
        controller.onCode = onCode
    }
}
