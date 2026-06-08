import SwiftUI

struct UnsupportedTabView: View {
    let title: String

    var body: some View {
        VStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.secondarySystemBackground))
                .frame(width: 88, height: 88)
                .overlay {
                    Image(systemName: "questionmark")
                        .font(.system(size: 32, weight: .regular))
                        .foregroundStyle(.secondary)
                }

            Text("Unsupported tab")
                .font(.title2.bold())

            Text(title)
                .font(.body)
                .foregroundStyle(.secondary)

            Text("This tab type isn't supported in the mobile app. Update Muxy Mobile or use the desktop app.")
                .font(.subheadline)
                .foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
