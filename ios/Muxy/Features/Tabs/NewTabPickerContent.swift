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
                CardSection {
                    actionRow("Add device", systemImage: "plus", action: onAddDevice)
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
                    .glassEffect(.regular, in: .rect(cornerRadius: SurfaceCorner.card, style: .continuous))
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
            CardSection {
                actionRow("Open SSH terminal", systemImage: "terminal") { onOpenSSH(device.id) }
            }
        } else if device.status == .needsPairing {
            CardSection {
                actionRow("Pair again", systemImage: "qrcode.viewfinder", action: onAddDevice)
            }
        } else if device.status == .offline {
            CardSection {
                actionRow("Reconnect", systemImage: "arrow.clockwise") { onRetry(device.id) }
            }
        } else if device.projects.isEmpty {
            Text(device.status == .loading ? "Loading projects…" : "Create a project on this device first.")
                .font(.subheadline)
                .foregroundStyle(theme.secondaryForeground)
                .padding(.horizontal, PageMargin.horizontal + CardMetrics.rowPadding)
        } else {
            CardSection {
                ForEach(device.projects) { project in
                    projectRow(project, on: device)
                }
            }
        }
    }

    private func actionRow(_ title: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            CardRow(title, systemImage: systemImage)
        }
        .buttonStyle(.cardRow)
    }

    private func projectRow(_ project: TabsHomeProject, on device: TabsHomeDevice) -> some View {
        Button {
            onCreate(device.id, project.id)
        } label: {
            CardRow(
                project.name,
                systemImage: "folder",
                subtitle: project.path.isEmpty ? nil : DisplayPath.abbreviated(project.path)
            ) {
                Image(systemName: "plus.circle")
                    .font(.title3)
                    .foregroundStyle(theme.secondaryForeground)
                    .accessibilityHidden(true)
            }
            .lineLimit(1)
            .truncationMode(.middle)
        }
        .buttonStyle(.cardRow)
        .disabled(!project.canCreate)
        .accessibilityLabel("New tab in \(project.name) on \(device.name)")
    }
}
