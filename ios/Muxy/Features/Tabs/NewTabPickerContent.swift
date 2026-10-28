import SwiftUI

struct NewTabPickerContent: View {
    let devices: [TabsHomeDevice]
    let isCreating: Bool
    let onCreate: (UUID, String) -> Void
    let onOpenSSH: (UUID) -> Void
    let onRetry: (UUID) -> Void
    let onAddDevice: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(devices) { device in
                    deviceSection(device)
                }
                TabsHomeCard {
                    TabsHomeActionRow(title: "Add device", systemImage: "plus", action: onAddDevice)
                }
                .padding(.top, 28)
            }
            .padding(.bottom, 32)
        }
        .background(theme.groupedBackground.ignoresSafeArea())
        .disabled(isCreating)
        .overlay {
            if isCreating {
                ProgressView("Creating tab…")
                    .padding(24)
                    .glassEffect(.regular, in: .rect(cornerRadius: 20))
            }
        }
    }

    private func deviceSection(_ device: TabsHomeDevice) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            TabsHomeDeviceHeader(device: device) { EmptyView() }
            deviceCard(device)
        }
        .padding(.top, 20)
    }

    @ViewBuilder
    private func deviceCard(_ device: TabsHomeDevice) -> some View {
        if device.status == .ssh {
            TabsHomeCard {
                TabsHomeActionRow(title: "Open SSH terminal", systemImage: "terminal") { onOpenSSH(device.id) }
            }
        } else if device.status == .needsPairing {
            TabsHomeCard {
                TabsHomeActionRow(title: "Pair again", systemImage: "qrcode.viewfinder", action: onAddDevice)
            }
        } else if device.status == .offline {
            TabsHomeCard {
                TabsHomeActionRow(title: "Reconnect", systemImage: "arrow.clockwise") { onRetry(device.id) }
            }
        } else if device.projects.isEmpty {
            Text(device.status == .loading ? "Loading projects…" : "Create a project on this device first.")
                .font(.subheadline)
                .foregroundStyle(theme.secondaryForeground)
                .padding(.horizontal, 20)
        } else {
            TabsHomeCard {
                ForEach(Array(device.projects.enumerated()), id: \.element.id) { index, project in
                    if index > 0 {
                        Rectangle()
                            .fill(theme.separator)
                            .frame(height: 0.5)
                            .padding(.leading, 66)
                    }
                    NewTabProjectRow(project: project) { onCreate(device.id, project.id) }
                        .accessibilityLabel("New tab in \(project.name) on \(device.name)")
                }
            }
        }
    }
}

private struct NewTabProjectRow: View {
    let project: TabsHomeProject
    let onCreate: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: onCreate) {
            HStack(spacing: 14) {
                Image(systemName: "folder")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(theme.secondaryForeground)
                    .frame(width: 36, height: 36)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(project.name)
                        .foregroundStyle(theme.foreground)
                        .lineLimit(1)
                    if !project.path.isEmpty {
                        Text(DisplayPath.abbreviated(project.path))
                            .font(.subheadline)
                            .foregroundStyle(theme.secondaryForeground)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "plus.circle")
                    .font(.title3)
                    .foregroundStyle(theme.secondaryForeground)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 62)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!project.canCreate)
        .opacity(project.canCreate ? 1 : 0.45)
    }
}
