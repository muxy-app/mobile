import SwiftUI

struct ConnectionRowView: View {
    let connection: Connection

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: iconName)
                .font(.title2)
                .foregroundStyle(theme.foreground)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(connection.name)
                    .font(.headline)
                    .foregroundStyle(theme.foreground)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(theme.secondaryForeground)
            }

            Spacer()
        }
        .padding(.vertical, 4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }

    private var iconName: String {
        connection.kind == .ssh ? "terminal" : "desktopcomputer"
    }

    private var subtitle: String {
        guard connection.kind == .ssh, let username = connection.sshConfig?.username else {
            return "\(connection.host):\(String(connection.port))"
        }
        return "\(username)@\(connection.host):\(String(connection.port))"
    }
}
