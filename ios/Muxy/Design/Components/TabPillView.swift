import SwiftUI

struct TabPillView<Item: TabStripItem>: View {
    let tab: Item
    let isSelected: Bool
    let onSelect: () -> Void
    let onClose: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 8) {
            TabKindIcon(kind: tab.tileKind, activity: tab.activity)

            Text(tab.displayTitle)
                .font(.subheadline.weight(isSelected ? .semibold : .regular))
                .foregroundStyle(isSelected ? theme.foreground : theme.secondaryForeground)
                .lineLimit(1)
                .truncationMode(.tail)

            if tab.activity.state != .idle {
                TabActivityBadge(activity: tab.activity)
            }

            if isSelected {
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(theme.secondaryForeground)
                        .frame(width: 22, height: 22)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close \(tab.displayTitle)")
            }
        }
        .padding(.leading, 12)
        .padding(.trailing, isSelected ? 6 : 12)
        .frame(height: 40)
        .frame(maxWidth: 220)
        .glassEffect(isSelected ? .regular.interactive() : .identity, in: Capsule())
        .contentShape(Capsule())
        .onTapGesture(perform: onSelect)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}
