import SwiftUI

struct TabsHomeDeviceSection: View {
    let device: TabsHomeDevice
    let isSearching: Bool
    @Binding var collapsedProjects: Set<String>
    @Binding var isRevealed: Bool
    let onOpenTab: (String, UUID) -> Void
    let onCreateTab: (String) -> Void
    let onNewTab: () -> Void
    let onAddDevice: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void
    let onOpenDevice: () -> Void
    let onRetry: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            notice
            ForEach(shownProjects) { project in
                TabsHomeProjectGroup(
                    project: project,
                    deviceName: device.name,
                    isExpanded: expandedBinding(for: project),
                    onOpenTab: { onOpenTab(project.id, $0) },
                    onCreateTab: { onCreateTab(project.id) }
                )
            }
            idleProjectsToggle
        }
        .padding(.top, 20)
    }

    private var header: some View {
        TabsHomeDeviceHeader(device: device) { menu }
    }

    private var menu: some View {
        Menu {
            if device.status == .needsPairing {
                Button("Pair again", systemImage: "qrcode.viewfinder", action: onAddDevice)
            } else if device.status != .ssh {
                Button("Projects", systemImage: "folder", action: onOpenDevice)
                Button(device.status == .offline ? "Reconnect" : "Refresh", systemImage: "arrow.clockwise", action: onRetry)
            }
            if device.canEdit {
                Button("Edit device", systemImage: "pencil", action: onEdit)
                Button("Remove device", systemImage: "trash", role: .destructive, action: onDelete)
            }
        } label: {
            Label("Manage \(device.name)", systemImage: "ellipsis")
                .labelStyle(.iconOnly)
                .font(.body.weight(.semibold))
                .foregroundStyle(theme.secondaryForeground)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
    }

    @ViewBuilder
    private var notice: some View {
        if device.status == .ssh {
            TabsHomeCard {
                TabsHomeActionRow(title: "Open SSH terminal", systemImage: "terminal", action: onOpenDevice)
            }
            .padding(.top, 8)
        } else if device.status == .connected, device.tabCount == 0, !isSearching {
            TabsHomeCard {
                HStack {
                    Text(device.projects.isEmpty ? "No projects on this device" : "No open tabs")
                        .foregroundStyle(theme.secondaryForeground)
                    Spacer()
                    if !device.projects.isEmpty {
                        Button("New tab", systemImage: "plus", action: onNewTab)
                            .buttonStyle(.glass)
                    }
                }
                .padding(16)
            }
            .padding(.top, 8)
        }
    }

    @ViewBuilder
    private var idleProjectsToggle: some View {
        let idle = device.idleProjects
        if !isSearching, !idle.isEmpty, device.tabCount > 0 {
            Button {
                withAnimation(.snappy) { isRevealed.toggle() }
            } label: {
                Text(isRevealed ? "Hide projects without tabs" : "\(idle.count) more \(idle.count == 1 ? "project" : "projects")")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(theme.secondaryForeground)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.plain)
            .padding(.top, 8)
        }
    }

    private var shownProjects: [TabsHomeProject] {
        guard !isSearching else { return device.projects }
        guard device.tabCount > 0 else { return [] }
        return isRevealed ? device.projects : device.visibleProjects
    }

    private func expandedBinding(for project: TabsHomeProject) -> Binding<Bool> {
        let key = "\(device.id.uuidString):\(project.id)"
        return Binding(
            get: { isSearching || !collapsedProjects.contains(key) },
            set: { expanded in
                if expanded {
                    collapsedProjects.remove(key)
                } else {
                    collapsedProjects.insert(key)
                }
            }
        )
    }
}

struct TabsHomeCard<Content: View>: View {
    @ViewBuilder let content: Content

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 0) {
            content
        }
        .background(theme.secondaryGroupedBackground, in: .rect(cornerRadius: 22, style: .continuous))
        .clipShape(.rect(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, 16)
    }
}

struct TabsHomeActionRow: View {
    let title: String
    let systemImage: String
    let action: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(theme.foreground)
                    .frame(width: 36, height: 36)
                    .accessibilityHidden(true)
                Text(title)
                    .foregroundStyle(theme.foreground)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 60)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct TabsHomeDeviceHeader<Trailing: View>: View {
    let device: TabsHomeDevice
    @ViewBuilder let trailing: Trailing

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: device.systemImage)
                .font(.title3)
                .foregroundStyle(theme.secondaryForeground)
                .accessibilityHidden(true)
            Text(device.name)
                .font(.title2.bold())
                .foregroundStyle(theme.foreground)
                .lineLimit(1)
                .accessibilityAddTraits(.isHeader)
                .accessibilityValue(device.status.label)
            Spacer(minLength: 8)
            if device.status == .loading {
                ProgressView()
                    .controlSize(.small)
            } else if device.status != .connected {
                TabsHomeConnectionStatus(status: device.status)
            }
            trailing
        }
        .padding(.leading, 20)
        .padding(.trailing, 8)
        .frame(minHeight: 44)
    }
}
