import SwiftUI

struct TabPillView<Item: TabStripItem>: View {
    let tab: Item
    let isSelected: Bool
    let onSelect: () -> Void
    let onClose: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 8) {
            TabKindTile(kind: tab.tileKind, activity: tab.activity, size: 26)

            Text(tab.title)
                .font(.subheadline.weight(isSelected ? .semibold : .regular))
                .foregroundStyle(isSelected ? theme.foreground : theme.secondaryForeground)
                .lineLimit(1)
                .truncationMode(.tail)

            if isSelected {
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(theme.secondaryForeground)
                        .frame(width: 22, height: 22)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close \(tab.title)")
            }
        }
        .padding(.leading, 6)
        .padding(.trailing, isSelected ? 6 : 12)
        .frame(height: 40)
        .frame(maxWidth: 220)
        .glassEffect(isSelected ? .regular.interactive() : .identity, in: .rect(cornerRadius: 13, style: .continuous))
        .contentShape(.rect(cornerRadius: 13))
        .onTapGesture(perform: onSelect)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}
