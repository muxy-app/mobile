import SwiftUI

struct ThemedToggleStyle: ToggleStyle {
    @Environment(\.appTheme) private var theme

    func makeBody(configuration: Configuration) -> some View {
        Toggle(configuration)
            .tint(theme.green)
    }
}
