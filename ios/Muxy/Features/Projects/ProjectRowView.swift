import SwiftUI

struct ProjectRowView: View {
    let project: Project
    let logoData: Data?

    var body: some View {
        HStack(spacing: 12) {
            icon
                .frame(width: 32, height: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(project.name)
                    .font(.headline)
                Text(project.path)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }

            Spacer()
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var icon: some View {
        if let logoData, let image = UIImage(data: logoData) {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
        } else {
            Image(systemName: project.icon ?? "folder")
                .font(.title2)
                .foregroundStyle(ProjectIconColor.color(for: project.iconColor))
        }
    }
}
