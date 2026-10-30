import SwiftUI

nonisolated enum SurfaceCorner {
    static let card: CGFloat = 24
    static let cardPadding: CGFloat = 11
    static let row: CGFloat = card - cardPadding
    static let segment: CGFloat = 4
    static let segmentGap: CGFloat = 2
    static let popover: CGFloat = 18
    static let popoverPadding: CGFloat = 8
    static let popoverRow: CGFloat = popover - popoverPadding
    static let thumbnail: CGFloat = 8
    static let appIcon: CGFloat = 36
}

nonisolated enum PageMargin {
    static let horizontal: CGFloat = 16
}

extension EnvironmentValues {
    @Entry var isActiveTabPage = true
}

extension View {
    func tabPage(isActive: Bool) -> some View {
        padding(.horizontal, PageMargin.horizontal)
            .padding(.top, 8)
            .environment(\.isActiveTabPage, isActive)
    }

    func keyboardIsland(bottomInset: CGFloat, background: Color) -> some View {
        mask(alignment: .top) {
            IslandShape(bottomInset: bottomInset) { $0.fill(.black) }
        }
        .background(alignment: .top) {
            IslandShape(bottomInset: bottomInset) { shape in
                shape
                    .fill(background)
                    .glassEffect(.regular, in: .rect(cornerRadius: SurfaceCorner.card, style: .continuous))
            }
        }
    }

    func tabPageChrome(_ theme: AppTheme) -> some View {
        background(theme.groupedBackground.ignoresSafeArea())
            .toolbarBackground(theme.groupedBackground, for: .navigationBar)
            .toolbarBackgroundVisibility(.visible, for: .navigationBar)
    }
}

private struct IslandShape<Content: View>: View {
    let bottomInset: CGFloat
    @ViewBuilder let content: (RoundedRectangle) -> Content

    var body: some View {
        GeometryReader { proxy in
            content(RoundedRectangle(cornerRadius: SurfaceCorner.card, style: .continuous))
                .frame(height: max(0, proxy.size.height - bottomInset))
                .animation(.smooth(duration: 0.25), value: bottomInset)
        }
    }
}
