import SwiftUI

struct TerminalTabView: View {
    @Bindable var session: TerminalSession

    @Environment(\.colorScheme) private var colorScheme
    @AppStorage(AppSettingKey.useNerdFont) private var useNerdFont = true
    @AppStorage(AppSettingKey.autoFocusTerminal) private var autoFocusTerminal = false
    @State private var fontSize = TerminalFont.defaultSize

    var body: some View {
        terminalSurface
            .onAppear { session.useClientTheme(colorScheme.clientTerminalTheme) }
            .onChange(of: colorScheme) { _, newValue in
                session.useClientTheme(newValue.clientTerminalTheme)
            }
            .onDisappear { session.dismissKeyboard() }
    }

    private var terminalSurface: some View {
        ZStack(alignment: .bottomTrailing) {
            TerminalViewContainer(
                session: session,
                theme: session.theme,
                fontSize: fontSize,
                useNerdFont: useNerdFont,
                autoFocusTerminal: autoFocusTerminal
            )
                .ignoresSafeArea(.keyboard)

            overlay

            if !session.isFollowingBottom {
                jumpToBottomButton
            }
        }
        .background(session.theme.background.asColor)
    }

    @ViewBuilder
    private var overlay: some View {
        switch session.ownership {
        case let .controlledElsewhere(deviceName):
            controlledElsewhere(deviceName: deviceName)
        case .disconnected:
            ContentUnavailableView {
                Label("Disconnected", systemImage: "wifi.slash")
            } description: {
                Text("Reconnect to continue using this terminal.")
            }
            .background(.background)
        case .idle, .takingOver, .owned:
            EmptyView()
        }
    }

    private func controlledElsewhere(deviceName: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "desktopcomputer")
                .font(.system(size: 44))
                .foregroundStyle(.secondary)

            VStack(spacing: 6) {
                Text("Controlled on \(deviceName)")
                    .font(.headline)
                Text("This terminal is active on your Mac.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            Button {
                session.takeControl()
            } label: {
                Text("Take Control")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(Color.terminalAccent))
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.background)
    }

    private var jumpToBottomButton: some View {
        Button {
            session.requestJumpToBottom()
        } label: {
            Image(systemName: "chevron.down")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(session.theme.foreground.asColor)
                .frame(width: 40, height: 40)
                .background(.regularMaterial, in: Circle())
                .overlay {
                    Circle()
                        .stroke(session.theme.foreground.asColor.opacity(0.18), lineWidth: 1)
                }
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .shadow(color: .black.opacity(0.18), radius: 8, y: 3)
        .padding(16)
    }
}

private extension UIColor {
    var asColor: Color { Color(self) }
}

extension Color {
    static let terminalAccent = Color.muxyBrand
}

private extension ColorScheme {
    var clientTerminalTheme: ClientTerminalTheme {
        switch self {
        case .light:
            return .light
        case .dark:
            return .dark
        @unknown default:
            return .dark
        }
    }
}
