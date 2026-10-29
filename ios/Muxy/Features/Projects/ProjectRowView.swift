import SwiftUI

struct ProjectRowView: View {
    let item: ProjectListItem

    var body: some View {
        CardRow(item.name, subtitle: DisplayPath.abbreviated(item.path)) {
            mark
                .frame(width: CardMetrics.iconWidth, height: CardMetrics.iconWidth)
                .padding(.leading, item.isNested ? Self.nestedIndent : 0)
        } trailing: {
            CardRowChevron()
        }
        .lineLimit(1)
        .truncationMode(.middle)
    }

    @ViewBuilder
    private var mark: some View {
        if let logo = item.logo, let image = UIImage(data: logo) {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .clipShape(.rect(cornerRadius: SurfaceCorner.thumbnail, style: .continuous))
                .accessibilityHidden(true)
        } else {
            Circle()
                .fill(ProjectIconColor.color(for: item.iconColor, stableFor: item.id))
                .frame(width: 8, height: 8)
                .accessibilityHidden(true)
        }
    }

    private static let nestedIndent: CGFloat = CardMetrics.iconWidth
}
