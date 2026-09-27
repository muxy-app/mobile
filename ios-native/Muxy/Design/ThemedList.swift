import SwiftUI

struct ThemedList<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        List {
            content.modifier(ThemedRowsModifier())
        }
        .modifier(ThemedGroupedContainerModifier())
    }
}

struct ThemedForm<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        Form {
            content.modifier(ThemedRowsModifier())
        }
        .modifier(ThemedGroupedContainerModifier())
    }
}

private struct ThemedRowsModifier: ViewModifier {
    @Environment(\.appTheme) private var theme

    func body(content: Content) -> some View {
        content
            .listRowBackground(theme.secondaryGroupedBackground)
            .listRowSeparatorTint(theme.separator)
    }
}

private struct ThemedGroupedContainerModifier: ViewModifier {
    @Environment(\.appTheme) private var theme

    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .background(theme.groupedBackground)
    }
}
