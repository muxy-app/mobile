import SwiftUI

struct ThemedEmptyState<Actions: View>: View {
    let title: String
    let systemImage: String
    let message: String
    @ViewBuilder let actions: () -> Actions

    @Environment(\.appTheme) private var theme

    init(
        title: String,
        systemImage: String,
        message: String,
        @ViewBuilder actions: @escaping () -> Actions = { EmptyView() }
    ) {
        self.title = title
        self.systemImage = systemImage
        self.message = message
        self.actions = actions
    }

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.system(size: 44))
                .foregroundStyle(theme.secondaryForeground)

            Text(title)
                .font(.title3.weight(.semibold))
                .foregroundStyle(theme.foreground)

            Text(message)
                .font(.subheadline)
                .foregroundStyle(theme.secondaryForeground)
                .multilineTextAlignment(.center)

            actions()
                .padding(.top, 4)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.background)
    }
}
