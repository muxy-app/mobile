import SwiftUI

struct ThemePickerView: View {
    @Bindable var settings: AppSettings

    var body: some View {
        CardList {
            CardSection(separatorInset: ThemeRowLayout.separatorInset) {
                ForEach(ThemeCatalog.all, id: \.name) { palette in
                    ThemeRow(palette: palette, isSelected: palette.name == settings.themePalette.name) {
                        settings.themeName = palette.name
                    }
                }
            }
        }
        .screenTitle("Terminal theme")
    }
}

private nonisolated enum ThemeRowLayout {
    static let swatchWidth: CGFloat = 40
    static let swatchHeight: CGFloat = 32
    static let stripHeight: CGFloat = 6
    static let separatorInset = CardMetrics.rowPadding + swatchWidth + CardMetrics.iconSpacing
}

private struct ThemeRow: View {
    let palette: ThemePalette
    let isSelected: Bool
    let onSelect: () -> Void

    @Environment(\.appTheme) private var theme

    var body: some View {
        Button(action: onSelect) {
            HStack(spacing: CardMetrics.iconSpacing) {
                swatch
                VStack(alignment: .leading, spacing: 6) {
                    Text(palette.name)
                        .foregroundStyle(theme.foreground)
                    ansiStrip
                }
                CardRowCheckmark()
                    .opacity(isSelected ? 1 : 0)
            }
            .padding(.horizontal, CardMetrics.rowPadding)
            .padding(.vertical, CardMetrics.rowVerticalPadding)
            .frame(maxWidth: .infinity, minHeight: CardMetrics.rowHeight, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.cardRow)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var swatch: some View {
        Text("Ab")
            .font(.subheadline.monospaced().weight(.semibold))
            .foregroundStyle(color(palette.foreground))
            .frame(width: ThemeRowLayout.swatchWidth, height: ThemeRowLayout.swatchHeight)
            .background(color(palette.background), in: .rect(cornerRadius: SurfaceCorner.thumbnail, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: SurfaceCorner.thumbnail, style: .continuous)
                    .strokeBorder(theme.separator, lineWidth: CardMetrics.separatorHeight)
            }
            .accessibilityHidden(true)
    }

    private var ansiStrip: some View {
        HStack(spacing: 0) {
            ForEach(palette.ansi.indices, id: \.self) { index in
                color(palette.ansi[index])
            }
        }
        .frame(height: ThemeRowLayout.stripHeight)
        .clipShape(Capsule())
        .accessibilityHidden(true)
    }

    private func color(_ rgb: UInt32) -> Color {
        Color(uiColor: UIColor(rgb: rgb))
    }
}
