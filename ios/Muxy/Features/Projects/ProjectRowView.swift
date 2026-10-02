import SwiftUI

struct ProjectRowView: View {
    let item: ProjectListItem

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            icon
                .frame(width: 32, height: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(item.name)
                    .font(.headline)
                    .foregroundStyle(theme.foreground)
                Text(item.path)
                    .font(.subheadline)
                    .foregroundStyle(theme.secondaryForeground)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }

            Spacer()
        }
        .padding(.vertical, 4)
        .padding(.leading, item.isNested ? Self.nestedIndent : 0)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var icon: some View {
        if let logo = item.logo, let image = UIImage(data: logo) {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
        } else {
            switch item.icon {
            case let .symbol(name):
                Image(systemName: name)
                    .font(.title2)
                    .foregroundStyle(ProjectIconColor.color(for: item.iconColor, fallback: theme.foreground))
            case let .emoji(emoji):
                Text(emoji)
                    .font(.title2)
            }
        }
    }

    private static let nestedIndent: CGFloat = 24
}
