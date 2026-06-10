import SwiftUI

struct UnsupportedTabView: View {
    let title: String

    @Environment(\.appTheme) private var theme

    var body: some View {
        VStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(theme.surface)
                .frame(width: 88, height: 88)
                .overlay {
                    Image(systemName: "questionmark")
                        .font(.system(size: 32, weight: .regular))
                        .foregroundStyle(theme.secondaryForeground)
                }

            Text("Unsupported tab")
                .font(.title2.bold())
                .foregroundStyle(theme.foreground)

            Text(title)
                .font(.body)
                .foregroundStyle(theme.secondaryForeground)

            Text("This tab type isn't supported in the mobile app. Update Muxy Mobile or use the desktop app.")
                .font(.subheadline)
                .foregroundStyle(theme.secondaryForeground)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background)
    }
}
