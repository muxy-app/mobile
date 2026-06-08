import SwiftUI

struct OnboardingView: View {
    let onSkip: () -> Void
    let onPairDesktop: () -> Void

    @State private var selection = 0

    private let slides = OnboardingSlide.all

    var body: some View {
        VStack(spacing: 0) {
            topBar

            TabView(selection: $selection) {
                ForEach(Array(slides.enumerated()), id: \.offset) { index, slide in
                    OnboardingSlideView(slide: slide)
                        .padding(.horizontal, 32)
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            footer
        }
        .background(Color(.systemBackground))
    }

    private var topBar: some View {
        HStack {
            Spacer()
            Button("Skip", action: onSkip)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
        }
        .padding(.top, 8)
    }

    private var footer: some View {
        VStack(spacing: 20) {
            HStack(spacing: 8) {
                ForEach(slides.indices, id: \.self) { index in
                    Capsule()
                        .fill(index == selection ? Color.muxyBrand : Color(.tertiaryLabel))
                        .frame(width: index == selection ? 22 : 6, height: 6)
                        .animation(.snappy(duration: 0.2), value: selection)
                }
            }
            .accessibilityHidden(true)

            Button(action: advance) {
                Text(isLastSlide ? "Pair your desktop" : "Continue")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .padding(.horizontal, 24)
                    .frame(height: 40)
                    .frame(minWidth: isLastSlide ? 190 : 144)
                    .background(Color.muxyBrand, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 11, style: .continuous)
                            .stroke(
                                LinearGradient(
                                    colors: [.white.opacity(0.18), .white.opacity(0)],
                                    startPoint: .top,
                                    endPoint: .bottom
                                ),
                                lineWidth: 1
                            )
                            .padding(1)
                    }
                    .shadow(color: .black.opacity(0.18), radius: 1, x: 0, y: 1)
            }
            .buttonStyle(.plain)
            .accessibilityHint(isLastSlide ? "Opens device pairing." : "Shows the next onboarding step.")
        }
        .padding(.horizontal, 24)
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

    var body: some View {
        Group {
            switch slide.content {
            case let .hero(symbolName, body):
                VStack(spacing: 18) {
                    if let symbolName {
                        Image(systemName: symbolName)
                            .font(.system(size: 46, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 96, height: 96)
                            .background(Color.muxyBrand, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                    } else {
                        Image("LaunchLogo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 164, height: 164)
                            .clipShape(RoundedRectangle(cornerRadius: 36, style: .continuous))
                    }

                    title

                    Text(body)
                        .font(.body)
                        .lineSpacing(2)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
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
            .font(.system(size: 28, weight: .bold))
            .multilineTextAlignment(.center)
            .foregroundStyle(.primary)
            .fixedSize(horizontal: false, vertical: true)
    }
}

private struct OnboardingRowView: View {
    let row: OnboardingRow

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: row.symbolName)
                .font(.system(size: 21, weight: .medium))
                .foregroundStyle(Color.muxyBrand)
                .frame(width: 44, height: 44)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(Color(.separator), lineWidth: 0.5)
                }

            VStack(alignment: .leading, spacing: 4) {
                Text(row.title)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.primary)

                Text(row.body)
                    .font(.subheadline)
                    .lineSpacing(2)
                    .foregroundStyle(.secondary)
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
