import SwiftUI

extension EnvironmentValues {
    @Entry var appTheme: AppTheme = .muxy
}

extension View {
    func themedSurface() -> some View {
        modifier(ThemedSurfaceModifier())
    }
}

private struct ThemedSurfaceModifier: ViewModifier {
    @Environment(\.appTheme) private var theme

    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .background(theme.background)
            .listRowBackground(
                theme.surface
                    .overlay(alignment: .bottom) {
                        theme.separator.frame(height: 0.5)
                    }
            )
            .listRowSeparator(.hidden)
    }
}
