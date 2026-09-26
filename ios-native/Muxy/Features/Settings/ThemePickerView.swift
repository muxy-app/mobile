import SwiftUI

struct ThemePickerView: View {
    @Bindable var settings: AppSettings

    var body: some View {
        List {
            ForEach(ThemeCatalog.all, id: \.name) { palette in
                Button {
                    settings.themeName = palette.name
                } label: {
                    ThemeRowView(palette: palette, isSelected: palette.name == settings.themePalette.name)
                }
                .buttonStyle(.plain)
            }
        }
        .themedSurface()
        .screenTitle("Theme")
    }
}

private struct ThemeRowView: View {
    let palette: ThemePalette
    let isSelected: Bool

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(spacing: 12) {
            swatch

            VStack(alignment: .leading, spacing: 6) {
                Text(palette.name)
                    .foregroundStyle(theme.foreground)
                ansiStrip
            }

            Spacer()

            if isSelected {
                Image(systemName: "checkmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(theme.accent)
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var swatch: some View {
        Text("Ab")
            .font(.system(.subheadline, design: .monospaced).weight(.semibold))
            .foregroundStyle(color(palette.foreground))
            .frame(width: 40, height: 32)
            .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(color(palette.background)))
            .overlay {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .stroke(theme.separator, lineWidth: 1)
            }
    }

    private var ansiStrip: some View {
        HStack(spacing: 2) {
            ForEach(palette.ansi.indices, id: \.self) { index in
                RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                    .fill(color(palette.ansi[index]))
                    .frame(height: 8)
            }
        }
    }

    private func color(_ rgb: UInt32) -> Color {
        Color(uiColor: UIColor(rgb: rgb))
    }
}
