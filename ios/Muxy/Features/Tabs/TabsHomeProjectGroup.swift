import SwiftUI

struct TabsHomeProjectGroup: View {
    let project: TabsHomeProject
    let deviceName: String
    @Binding var isExpanded: Bool
    let onOpenTab: (UUID) -> Void
    let onCreateTab: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            header
            if isExpanded, !project.tabs.isEmpty {
                TabsHomeCard {
                    ForEach(Array(project.tabs.enumerated()), id: \.element.id) { index, tab in
                        if index > 0 {
                            Rectangle()
                                .fill(theme.separator)
                                .frame(height: 0.5)
                                .padding(.leading, 66)
                        }
                        TabsHomeTabRow(tab: tab) { onOpenTab(tab.id) }
                            .accessibilityLabel("\(tab.title), \(project.name), \(deviceName)")
                    }
                }
            }
        }
        .padding(.top, 12)
    }

    private var header: some View {
        HStack(spacing: 0) {
            Text(project.name)
                .font(.headline)
                .foregroundStyle(theme.secondaryForeground)
                .lineLimit(1)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 8)
            Button(action: onCreateTab) {
                Label("New tab in \(project.name)", systemImage: "plus.circle")
                    .labelStyle(.iconOnly)
                    .font(.title3)
                    .foregroundStyle(theme.secondaryForeground)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!project.canCreate)
            if !project.tabs.isEmpty {
                Button {
                    withAnimation(.snappy) { isExpanded.toggle() }
                } label: {
                    Label(isExpanded ? "Collapse \(project.name)" : "Expand \(project.name)", systemImage: "chevron.down")
                        .labelStyle(.iconOnly)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(theme.secondaryForeground)
                        .rotationEffect(.degrees(isExpanded ? 0 : -90))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.leading, 20)
        .padding(.trailing, 8)
    }
}

struct TabsHomeTabRow: View {
    let tab: TabsHomeTab
    let onOpen: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 14) {
                TabKindTile(kind: tab.kind, activity: tab.activity)
                VStack(alignment: .leading, spacing: 2) {
                    Text(tab.title)
                        .foregroundStyle(theme.foreground)
                        .lineLimit(1)
                    if !tab.detail.isEmpty {
                        Text(tab.detail)
                            .font(.subheadline)
                            .foregroundStyle(detailColor)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if tab.activity.state == .working {
                    Image(systemName: "ellipsis")
                        .font(.body.weight(.bold))
                        .foregroundStyle(theme.secondaryForeground)
                        .symbolEffect(.variableColor.iterative, options: .repeating)
                        .accessibilityLabel("Working")
                }
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 62)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("Opens this tab")
    }

    private var detailColor: Color {
        switch tab.activity.state {
        case .waiting: .orange
        case .finished: .blue
        case .idle, .working: theme.secondaryForeground
        }
    }
}
