import SwiftUI

struct ThemedSectionHeader: View {
    private let title: String

    @Environment(\.appTheme) private var theme

    init(_ title: String) {
        self.title = title
    }

    var body: some View {
        Text(title)
            .foregroundStyle(theme.secondaryForeground)
    }
}
