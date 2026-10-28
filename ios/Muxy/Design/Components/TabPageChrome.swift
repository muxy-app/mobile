import SwiftUI

private let islandCornerRadius: CGFloat = 24

extension View {
    func tabPageIsland() -> some View {
        clipShape(.rect(cornerRadius: islandCornerRadius, style: .continuous))
            .padding(.horizontal, 8)
    }

    func keyboardIsland(bottomInset: CGFloat) -> some View {
        mask(alignment: .top) {
            GeometryReader { proxy in
                RoundedRectangle(cornerRadius: islandCornerRadius, style: .continuous)
                    .frame(height: max(0, proxy.size.height - bottomInset))
                    .animation(.smooth(duration: 0.25), value: bottomInset)
            }
        }
    }

    func tabPageChrome(_ theme: AppTheme) -> some View {
        background(theme.groupedBackground)
            .toolbarBackground(theme.groupedBackground, for: .navigationBar)
            .toolbarBackgroundVisibility(.visible, for: .navigationBar)
    }
}
