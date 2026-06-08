import SwiftUI

struct DeviceRowView: View {
    let device: Device

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "desktopcomputer")
                .font(.title2)
                .foregroundStyle(.tint)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(device.name)
                    .font(.headline)
                Text("\(device.host):\(String(device.port))")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            statusBadge
        }
        .padding(.vertical, 4)
    }

    private var statusBadge: some View {
        Group {
            switch device.pairingState {
            case .paired:
                Label("Paired", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
            case .notPaired:
                Label("Not paired", systemImage: "exclamationmark.circle")
                    .foregroundStyle(.orange)
            }
        }
        .labelStyle(.iconOnly)
        .font(.title3)
    }
}
