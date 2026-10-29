import SwiftUI

struct OnboardingView: View {
    let onSkip: () -> Void
    let onPairDesktop: () -> Void

    @Environment(\.appTheme) private var theme
    @State private var selection = 0

    private let slides = OnboardingSlide.all

    var body: some View {
        VStack(spacing: 0) {
            topBar

            TabView(selection: $selection) {
                ForEach(Array(slides.enumerated()), id: \.offset) { index, slide in
                    OnboardingSlideView(slide: slide)
                        .padding(.horizontal, PageMargin.horizontal)
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            footer
        }
        .background(theme.groupedBackground.ignoresSafeArea())
    }

    private var topBar: some View {
        HStack {
            Spacer()
            Button("Skip", action: onSkip)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(theme.secondaryForeground)
                .frame(minHeight: 44)
        }
        .padding(.horizontal, PageMargin.horizontal)
        .padding(.top, 8)
    }

    private var footer: some View {
        VStack(spacing: 20) {
            HStack(spacing: 8) {
                ForEach(slides.indices, id: \.self) { index in
                    Capsule()
                        .fill(index == selection ? theme.accent : theme.separator)
                        .frame(width: index == selection ? 22 : 6, height: 6)
                        .animation(.snappy(duration: 0.2), value: selection)
                }
            }
            .accessibilityHidden(true)

            Button(action: advance) {
                Text(isLastSlide ? "Pair your desktop" : "Continue")
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(ThemedProminentButtonStyle())
            .accessibilityHint(isLastSlide ? "Opens device pairing." : "Shows the next onboarding step.")
        }
        .padding(.horizontal, PageMargin.horizontal)
        .padding(.bottom, 16)
    }

    private var isLastSlide: Bool {
        selection == slides.count - 1
    }

    private func advance() {
        if isLastSlide {
            onPairDesktop()
        } else {
            withAnimation(.snappy(duration: 0.25)) {
                selection += 1
            }
        }
    }
}

private struct OnboardingSlideView: View {
    let slide: OnboardingSlide

    @Environment(\.appTheme) private var theme

    var body: some View {
        Group {
            switch slide.content {
            case let .hero(symbolName, body):
                VStack(spacing: 18) {
                    if let symbolName {
                        Image(systemName: symbolName)
                            .font(.largeTitle.weight(.semibold))
                            .imageScale(.large)
                            .foregroundStyle(theme.onAccent)
                            .frame(width: 96, height: 96)
                            .background(theme.accent, in: .rect(cornerRadius: SurfaceCorner.card, style: .continuous))
                    } else {
                        Image("LaunchLogo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 164, height: 164)
                            .clipShape(.rect(cornerRadius: SurfaceCorner.appIcon, style: .continuous))
                    }

                    title

                    Text(body)
                        .font(.body)
                        .lineSpacing(2)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(theme.secondaryForeground)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            case let .rows(rows):
                VStack(spacing: 24) {
                    title

                    VStack(spacing: 18) {
                        ForEach(rows) { row in
                            OnboardingRowView(row: row)
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }

    private var title: some View {
        Text(slide.title)
            .font(.title.bold())
            .multilineTextAlignment(.center)
            .foregroundStyle(theme.foreground)
            .fixedSize(horizontal: false, vertical: true)
    }
}

private struct OnboardingRowView: View {
    let row: OnboardingRow

    @Environment(\.appTheme) private var theme

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: CardMetrics.iconSpacing) {
            Image(systemName: row.symbolName)
                .font(.title3.weight(.medium))
                .foregroundStyle(theme.secondaryForeground)
                .frame(width: CardMetrics.iconWidth)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text(row.title)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(theme.foreground)

                Text(row.body)
                    .font(.subheadline)
                    .lineSpacing(2)
                    .foregroundStyle(theme.secondaryForeground)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private struct OnboardingSlide {
    let title: String
    let content: Content

    enum Content {
        case hero(symbolName: String?, body: String)
        case rows([OnboardingRow])
    }

    static let all: [OnboardingSlide] = [
        OnboardingSlide(
            title: "Welcome to Muxy",
            content: .hero(
                symbolName: nil,
                body: "The remote control for your desktop terminal. Drive sessions, switch projects, and ship changes from your phone."
            )
        ),
        OnboardingSlide(
            title: "How it works",
            content: .rows([
                OnboardingRow(
                    symbolName: "wifi",
                    title: "Same network",
                    body: "Your phone and desktop talk directly over your local network."
                ),
                OnboardingRow(
                    symbolName: "switch.2",
                    title: "Enable the Mobile server",
                    body: "On your desktop: Muxy > Settings > Mobile, then toggle the server on."
                ),
                OnboardingRow(
                    symbolName: "bolt",
                    title: "Stay in sync",
                    body: "Open projects, run commands, and review changes in real time."
                )
            ])
        ),
        OnboardingSlide(
            title: "Pair your desktop",
            content: .hero(
                symbolName: "rectangle.grid.3x2",
                body: "Enter your desktop's IP address and the port shown in Muxy's Mobile settings. Default port is 4865."
            )
        )
    ]
}

private struct OnboardingRow: Identifiable {
    let id = UUID()
    let symbolName: String
    let title: String
    let body: String
}
