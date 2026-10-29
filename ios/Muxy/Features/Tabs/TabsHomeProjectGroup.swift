import SwiftUI

struct TabsHomeProjectGroup: View {
    let project: TabsHomeProject
    let deviceName: String
    @Binding var isExpanded: Bool
    let onOpenTab: (UUID) -> Void
    let onCreateTab: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            header
            if showsTabs {
                ForEach(project.tabs) { tab in
                    TabsHomeTabRow(tab: tab) { onOpenTab(tab.id) }
                        .accessibilityLabel("\(tab.title), \(project.name), \(deviceName)")
                }
            }
        }
        .padding(.top, 4)
        .padding(.horizontal, SurfaceCorner.cardPadding)
        .padding(.bottom, showsTabs ? SurfaceCorner.cardPadding : 4)
        .glassEffect(.regular, in: .rect(cornerRadius: SurfaceCorner.card, style: .continuous))
        .padding(.horizontal, PageMargin.horizontal)
    }

    private var showsTabs: Bool {
        isExpanded && !project.tabs.isEmpty
    }

    private var header: some View {
        HStack(spacing: 0) {
            Button(action: toggle) {
                HStack(spacing: 8) {
                    Circle()
                        .fill(ProjectIconColor.color(for: project.color, stableFor: project.id))
                        .frame(width: 8, height: 8)
                    Text(project.name)
                        .font(.headline)
                        .foregroundStyle(theme.foreground)
                        .lineLimit(1)
                    if !isExpanded, !project.tabs.isEmpty {
                        Text(project.tabs.count == 1 ? "1 tab" : "\(project.tabs.count) tabs")
                            .font(.subheadline)
                            .foregroundStyle(theme.secondaryForeground)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 4)
                }
                .padding(.leading, 6)
                .frame(minHeight: 40)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(project.tabs.isEmpty)
            .accessibilityLabel(project.name)
            .accessibilityAddTraits(.isHeader)
            .accessibilityValue(project.tabs.isEmpty ? "" : isExpanded ? "Expanded" : "Collapsed")
            .accessibilityHint(project.tabs.isEmpty ? "" : isExpanded ? "Collapses this project" : "Expands this project")
            Button(action: onCreateTab) {
                Label("New tab in \(project.name)", systemImage: "plus")
                    .labelStyle(.iconOnly)
                    .font(.body.weight(.medium))
                    .foregroundStyle(theme.secondaryForeground)
                    .frame(width: 40, height: 40)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!project.canCreate)
            if !project.tabs.isEmpty {
                Button(action: toggle) {
                    Image(systemName: "chevron.down")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(theme.secondaryForeground)
                        .rotationEffect(.degrees(isExpanded ? 0 : -90))
                        .frame(width: 40, height: 40)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityHidden(true)
            }
        }
    }

    private func toggle() {
        withAnimation(.snappy) { isExpanded.toggle() }
    }
}

struct TabsHomeTabRow: View {
    let tab: TabsHomeTab
    let onOpen: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 10) {
                TabKindIcon(kind: tab.kind, activity: tab.activity)
                Text(tab.title)
                    .foregroundStyle(theme.foreground)
                    .lineLimit(1)
                    .layoutPriority(1)
                Spacer(minLength: 8)
                trailing
            }
            .padding(.leading, 12)
            .padding(.trailing, 14)
            .frame(minHeight: 48)
            .background(theme.foreground.opacity(0.06), in: .rect(cornerRadius: SurfaceCorner.row, style: .continuous))
            .contentShape(.rect(cornerRadius: SurfaceCorner.row, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityValue(tab.activity.state == .idle ? tab.detail : tab.activity.statusLabel ?? "")
        .accessibilityHint("Opens this tab")
    }

    @ViewBuilder
    private var trailing: some View {
        if tab.activity.state != .idle {
            TabActivityBadge(activity: tab.activity)
        } else if !tab.detail.isEmpty {
            Text(tab.detail)
                .font(.footnote.monospaced())
                .foregroundStyle(theme.secondaryForeground)
                .lineLimit(1)
                .truncationMode(.head)
        }
    }
}
