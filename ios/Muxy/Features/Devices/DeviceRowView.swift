import SwiftUI

struct DeviceRowView: View {
    let device: Device

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "desktopcomputer")
                .font(.title2)
                .foregroundStyle(theme.foreground)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(device.name)
                    .font(.headline)
                    .foregroundStyle(theme.foreground)
                Text("\(device.host):\(String(device.port))")
                    .font(.subheadline)
                    .foregroundStyle(theme.secondaryForeground)
            }

            Spacer()
        }
        .padding(.vertical, 4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}
