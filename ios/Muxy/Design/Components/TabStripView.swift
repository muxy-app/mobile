import SwiftUI

protocol TabStripItem: Identifiable {
    var title: String { get }
    var systemImage: String { get }
    var tileKind: TabTileKind { get }
    var activity: TabActivity { get }
}

struct TabStripView<Item: TabStripItem>: View {
    let tabs: [Item]
    let selectedTabID: Item.ID?
    let onSelect: (Item) -> Void
    let onClose: (Item) -> Void
    let onCreate: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            ScrollViewReader { proxy in
                GlassEffectContainer(spacing: 8) {
                    HStack(spacing: 4) {
                        ForEach(tabs) { tab in
                            TabPillView(
                                tab: tab,
                                isSelected: tab.id == selectedTabID,
                                onSelect: { onSelect(tab) },
                                onClose: { onClose(tab) }
                            )
                            .id(tab.id)
                        }
                        newTabButton
                    }
                    .padding(.horizontal, PageMargin.horizontal)
                    .padding(.top, 8)
                }
                .onChange(of: selectedTabID) { _, newValue in
                    guard let newValue else { return }
                    withAnimation { proxy.scrollTo(newValue, anchor: .center) }
                }
            }
        }
        .scrollClipDisabled()
    }

    private var newTabButton: some View {
        Button(action: onCreate) {
            Image(systemName: "plus.circle")
                .font(.title3)
                .foregroundStyle(theme.secondaryForeground)
                .frame(width: 40, height: 40)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("New Tab")
    }
}
