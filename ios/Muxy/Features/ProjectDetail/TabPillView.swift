import SwiftUI

struct TabPillView: View {
    let tab: Tab
    let isSelected: Bool
    let onSelect: () -> Void
    let onClose: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.footnote)
                .foregroundStyle(.secondary)

            Text(tab.title)
                .font(.subheadline)
                .lineLimit(1)
                .truncationMode(.tail)

            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 12)
        .padding(.trailing, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: 200)
        .background(background)
        .clipShape(Capsule())
        .contentShape(Capsule())
        .onTapGesture(perform: onSelect)
    }

    private var icon: String {
        switch tab.kind {
        case .terminal:
            return "terminal"
        case .vcs:
            return "arrow.triangle.branch"
        case .unsupported:
            return "questionmark.circle"
        }
    }

    @ViewBuilder
    private var background: some View {
        if isSelected {
            Capsule().fill(Color(.systemGray4))
        } else {
            Capsule().fill(Color(.quaternarySystemFill))
        }
    }
}
