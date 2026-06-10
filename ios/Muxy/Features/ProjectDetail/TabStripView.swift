import SwiftUI

struct TabStripView: View {
    let tabs: [Tab]
    let selectedTabID: UUID?
    let onSelect: (Tab) -> Void
    let onClose: (Tab) -> Void
    let onCreate: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            ScrollViewReader { proxy in
                HStack(spacing: 8) {
                    ForEach(tabs) { tab in
                        TabPillView(
                            tab: tab,
                            isSelected: tab.id == selectedTabID,
                            onSelect: { onSelect(tab) },
                            onClose: { onClose(tab) }
                        )
                        .id(tab.id)
                    }

                    Button(action: onCreate) {
                        Image(systemName: "plus")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(theme.foreground)
                            .frame(width: 32, height: 32)
                            .background(Circle().fill(theme.surface))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .onChange(of: selectedTabID) { _, newValue in
                    guard let newValue else { return }
                    withAnimation { proxy.scrollTo(newValue, anchor: .center) }
                }
            }
        }
        .background(theme.background)
    }
}
