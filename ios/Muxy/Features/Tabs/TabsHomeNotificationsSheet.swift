import SwiftUI

struct TabsHomeNotificationsSheet: View {
    let items: [TabsHomeAttention]
    let onOpen: (TabsHomeAttention) -> Void

    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                if !items.isEmpty {
                    TabsHomeCard {
                        ForEach(items) { item in
                            if item.id != items.first?.id {
                                Divider()
                                    .padding(.leading, 66)
                            }
                            TabsHomeNotificationRow(item: item) { onOpen(item) }
                        }
                    }
                    .padding(.top, 8)
                }
            }
            .background(theme.groupedBackground.ignoresSafeArea())
            .overlay {
                if items.isEmpty {
                    ContentUnavailableView(
                        "No notifications",
                        systemImage: "bell.slash",
                        description: Text("Agents waiting for you or finished show up here.")
                    )
                }
            }
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", systemImage: "checkmark") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

private struct TabsHomeNotificationRow: View {
    let item: TabsHomeAttention
    let onOpen: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 14) {
                TabKindIcon(kind: item.tab.kind, activity: item.tab.activity, size: 22)
                    .frame(width: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.tab.title)
                        .foregroundStyle(theme.foreground)
                        .lineLimit(1)
                    Text(item.tab.activity.statusLabel ?? "")
                        .font(.subheadline)
                        .foregroundStyle(item.tab.activity.state == .waiting ? Color.orange : theme.green)
                        .lineLimit(1)
                    Text(context)
                        .font(.caption)
                        .foregroundStyle(theme.secondaryForeground)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Opens this tab")
    }

    private var context: String {
        let parts = [item.projectName, item.deviceName]
        guard let since = item.tab.activity.since else { return parts.joined(separator: " · ") }
        return (parts + [since.formatted(.relative(presentation: .numeric, unitsStyle: .narrow))]).joined(separator: " · ")
    }
}
