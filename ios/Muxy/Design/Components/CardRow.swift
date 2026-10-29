import SwiftUI

struct CardRow<Leading: View, Trailing: View>: View {
    private let title: String
    private let subtitle: String?
    private let value: String?
    private let leading: Leading
    private let trailing: Trailing

    @Environment(\.appTheme) private var theme

    init(
        _ title: String,
        subtitle: String? = nil,
        value: String? = nil,
        @ViewBuilder leading: () -> Leading,
        @ViewBuilder trailing: () -> Trailing
    ) {
        self.title = title
        self.subtitle = subtitle
        self.value = value
        self.leading = leading()
        self.trailing = trailing()
    }

    var body: some View {
        HStack(spacing: CardMetrics.iconSpacing) {
            leading
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .foregroundStyle(theme.foreground)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(theme.secondaryForeground)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let value, !value.isEmpty {
                Text(value)
                    .foregroundStyle(theme.secondaryForeground)
                    .lineLimit(1)
            }
            trailing
        }
        .padding(.horizontal, CardMetrics.rowPadding)
        .padding(.vertical, CardMetrics.rowVerticalPadding)
        .frame(maxWidth: .infinity, minHeight: CardMetrics.rowHeight, alignment: .leading)
        .contentShape(Rectangle())
    }
}

extension CardRow where Leading == CardRowSymbol?, Trailing == CardRowChevron {
    init(_ title: String, systemImage: String? = nil, subtitle: String? = nil, value: String? = nil) {
        self.init(title, subtitle: subtitle, value: value) {
            if let systemImage {
                CardRowSymbol(systemImage)
            }
        } trailing: {
            CardRowChevron()
        }
    }
}

extension CardRow where Leading == CardRowSymbol? {
    init(
        _ title: String,
        systemImage: String? = nil,
        subtitle: String? = nil,
        value: String? = nil,
        @ViewBuilder trailing: () -> Trailing
    ) {
        self.init(title, subtitle: subtitle, value: value) {
            if let systemImage {
                CardRowSymbol(systemImage)
            }
        } trailing: {
            trailing()
        }
    }
}

struct CardRowSymbol: View {
    let systemImage: String

    @Environment(\.appTheme) private var theme

    init(_ systemImage: String) {
        self.systemImage = systemImage
    }

    var body: some View {
        Image(systemName: systemImage)
            .font(.body.weight(.medium))
            .foregroundStyle(theme.secondaryForeground)
            .frame(width: CardMetrics.iconWidth)
            .accessibilityHidden(true)
    }
}

struct CardRowChevron: View {
    var body: some View {
        Image(systemName: "chevron.right")
            .font(.footnote.weight(.semibold))
            .foregroundStyle(.tertiary)
            .accessibilityHidden(true)
    }
}

struct CardRowCheckmark: View {
    @Environment(\.appTheme) private var theme

    var body: some View {
        Image(systemName: "checkmark")
            .font(.body.weight(.semibold))
            .foregroundStyle(theme.accent)
            .accessibilityHidden(true)
    }
}

struct CardRowButtonStyle: ButtonStyle {
    @Environment(\.appTheme) private var theme
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? theme.foreground.opacity(0.06) : .clear)
            .opacity(isEnabled ? 1 : 0.45)
    }
}

extension ButtonStyle where Self == CardRowButtonStyle {
    static var cardRow: CardRowButtonStyle { CardRowButtonStyle() }
}
