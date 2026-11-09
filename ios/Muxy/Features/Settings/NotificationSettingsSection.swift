import MuxyPush
import SwiftUI
import UIKit

struct NotificationSettingsSection: View {
    let push: PushCenter

    @Environment(\.openURL) private var openURL

    var body: some View {
        CardSection(
            "Notifications",
            footer: "Muxy tells you when an agent needs you or finishes while the app is closed. Notifications are end-to-end encrypted, so muxy.app relays them without seeing what they say."
        ) {
            permissionRow
            if servers.isEmpty {
                CardRow("Pair a computer running Muxy 2 to get notifications.", systemImage: "desktopcomputer")
            }
            ForEach(servers, id: \.serverId) { server in
                CardRow(server.name, systemImage: "desktopcomputer", subtitle: summary(for: server)) {
                    Toggle(server.name, isOn: binding(for: server))
                        .labelsHidden()
                }
            }
        }
    }

    @ViewBuilder
    private var permissionRow: some View {
        switch push.authorization {
        case .notDetermined:
            Button {
                Task { await push.requestPermission() }
            } label: {
                CardRow("Allow notifications", systemImage: "bell.badge")
            }
            .buttonStyle(.cardRow)
        case .denied:
            Button(action: openSystemSettings) {
                CardRow(
                    "Turn on in iOS Settings",
                    systemImage: "bell.slash",
                    subtitle: "Notifications are off for Muxy."
                )
            }
            .buttonStyle(.cardRow)
        case .allowed:
            EmptyView()
        }
    }

    private var servers: [PushServer] {
        push.servers.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    private func summary(for server: PushServer) -> String {
        PushServerSummary.text(
            authorization: push.authorization,
            isEnabled: server.isEnabled,
            status: push.status(for: server.serverId),
            serverName: server.name
        )
    }

    private func binding(for server: PushServer) -> Binding<Bool> {
        Binding(
            get: { push.isEnabled(server.serverId) },
            set: { enabled in Task { await push.setEnabled(enabled, for: server.serverId) } }
        )
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openNotificationSettingsURLString) else { return }
        openURL(url)
    }
}
