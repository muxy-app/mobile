import SwiftUI

struct UnsupportedTabView: View {
    let title: String

    @Environment(\.appTheme) private var theme

    var body: some View {
        ThemedEmptyState(
            title: "Unsupported Tab",
            systemImage: "questionmark.square.dashed",
            message: "“\(title)” isn't supported in the mobile app. Update Muxy Mobile or use the desktop app."
        )
        .background(theme.groupedBackground)
    }
}
