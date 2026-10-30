import SwiftUI

nonisolated enum CardMetrics {
    static let rowHeight: CGFloat = 48
    static let rowPadding: CGFloat = 16
    static let rowVerticalPadding: CGFloat = 8
    static let iconWidth: CGFloat = 24
    static let iconSpacing: CGFloat = 12
    static let separatorHeight: CGFloat = 0.5
    static let textInset = rowPadding
    static let iconTextInset = rowPadding + iconWidth + iconSpacing
    static let sectionSpacing: CGFloat = 24
    static let headerSpacing: CGFloat = 8
}

extension View {
    func cardSurface() -> some View {
        modifier(CardSurfaceModifier())
    }
}

private struct CardSurfaceModifier: ViewModifier {
    @Environment(\.appTheme) private var theme

    func body(content: Content) -> some View {
        content
            .background(theme.secondaryGroupedBackground, in: .rect(cornerRadius: SurfaceCorner.card, style: .continuous))
            .clipShape(.rect(cornerRadius: SurfaceCorner.card, style: .continuous))
    }
}

struct Card<Content: View>: View {
    private let separatorInset: CGFloat
    private let content: Content

    init(separatorInset: CGFloat = CardMetrics.iconTextInset, @ViewBuilder content: () -> Content) {
        self.separatorInset = separatorInset
        self.content = content()
    }

    var body: some View {
        VStack(spacing: 0) {
            Group(subviews: content) { subviews in
                ForEach(subviews) { subview in
                    if subview.id != subviews.first?.id {
                        CardSeparator(inset: separatorInset)
                    }
                    subview
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardSurface()
    }
}

struct SegmentedCard<Content: View>: View {
    private let content: Content

    @Environment(\.appTheme) private var theme

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(spacing: SurfaceCorner.segmentGap) {
            Group(subviews: content) { subviews in
                ForEach(Array(subviews.enumerated()), id: \.element.id) { index, subview in
                    let shape = RowPosition.of(index: index, count: subviews.count).shape
                    subview
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(theme.secondaryGroupedBackground, in: shape)
                        .clipShape(shape)
                }
            }
        }
    }
}

struct CardSeparator: View {
    var inset: CGFloat = CardMetrics.iconTextInset

    @Environment(\.appTheme) private var theme

    var body: some View {
        Rectangle()
            .fill(theme.separator)
            .frame(height: CardMetrics.separatorHeight)
            .padding(.leading, inset)
            .accessibilityHidden(true)
    }
}

struct CardSection<Content: View>: View {
    private let title: String?
    private let detail: String?
    private let footer: String?
    private let separatorInset: CGFloat
    private let content: Content

    @Environment(\.appTheme) private var theme

    init(
        _ title: String? = nil,
        detail: String? = nil,
        footer: String? = nil,
        separatorInset: CGFloat = CardMetrics.iconTextInset,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.detail = detail
        self.footer = footer
        self.separatorInset = separatorInset
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: CardMetrics.headerSpacing) {
            if let title {
                CardSectionHeader(title, detail: detail)
            }
            Card(separatorInset: separatorInset) { content }
            if let footer {
                Text(footer)
                    .font(.footnote)
                    .foregroundStyle(theme.secondaryForeground)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, CardMetrics.rowPadding)
            }
        }
        .padding(.horizontal, PageMargin.horizontal)
    }
}

struct CardSectionHeader: View {
    private let title: String
    private let detail: String?

    @Environment(\.appTheme) private var theme

    init(_ title: String, detail: String? = nil) {
        self.title = title
        self.detail = detail
    }

    var body: some View {
        HStack {
            ThemedSectionHeader(title)
            Spacer(minLength: 8)
            if let detail {
                Text(detail)
                    .monospacedDigit()
                    .foregroundStyle(theme.secondaryForeground)
            }
        }
        .font(.subheadline.weight(.semibold))
        .padding(.horizontal, CardMetrics.rowPadding)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

struct CardList<Content: View>: View {
    private let content: Content

    @Environment(\.appTheme) private var theme

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CardMetrics.sectionSpacing) {
                content
            }
            .padding(.top, CardMetrics.headerSpacing)
            .padding(.bottom, 32)
        }
        .background(theme.groupedBackground.ignoresSafeArea())
        .toggleStyle(ThemedToggleStyle())
    }
}

struct CardMessageRow: View {
    private let message: String
    private let systemImage: String
    private let tint: Color?

    @Environment(\.appTheme) private var theme

    init(_ message: String, systemImage: String = "exclamationmark.triangle.fill", tint: Color? = nil) {
        self.message = message
        self.systemImage = systemImage
        self.tint = tint
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: CardMetrics.iconSpacing) {
            Image(systemName: systemImage)
                .frame(width: CardMetrics.iconWidth)
                .accessibilityHidden(true)
            Text(message)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
        }
        .font(.subheadline)
        .foregroundStyle(tint ?? theme.red)
        .padding(.horizontal, CardMetrics.rowPadding)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, minHeight: CardMetrics.rowHeight, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}
