import SwiftUI

struct TabPillView<Item: TabStripItem>: View {
    let tab: Item
    let isSelected: Bool
    let onSelect: () -> Void
    let onClose: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: tab.systemImage)
                .font(.footnote)
                .foregroundStyle(secondaryForeground)

            Text(tab.title)
                .font(.subheadline)
                .foregroundStyle(foreground)
                .lineLimit(1)
                .truncationMode(.tail)

            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(secondaryForeground)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close \(tab.title)")
        }
        .padding(.leading, 12)
        .padding(.trailing, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: 200)
        .background(Capsule().fill(isSelected ? theme.selectionBackground : theme.surface))
        .clipShape(Capsule())
        .contentShape(Capsule())
        .onTapGesture(perform: onSelect)
    }

    private var foreground: Color {
        isSelected ? theme.selectionForeground : theme.foreground
    }

    private var secondaryForeground: Color {
        isSelected ? theme.selectionForeground.opacity(0.7) : theme.secondaryForeground
    }
}
