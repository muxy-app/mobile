import SwiftUI

struct TerminalTabView: View {
    @Bindable var session: TerminalSession

    @Environment(\.appTheme) private var appTheme
    @AppStorage(AppSettingKey.useNerdFont) private var useNerdFont = true
    @AppStorage(AppSettingKey.autoFocusTerminal) private var autoFocusTerminal = false
    @State private var fontSize = TerminalFont.defaultSize

    var body: some View {
        terminalSurface
            .onAppear { session.useClientTheme(ClientTerminalTheme(event: appTheme.event)) }
            .onChange(of: appTheme) { _, newValue in
                session.useClientTheme(ClientTerminalTheme(event: newValue.event))
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
            ThemedEmptyState(
                title: "Disconnected",
                systemImage: "wifi.slash",
                message: "Reconnect to continue using this terminal."
            )
        case .idle, .takingOver, .owned:
            EmptyView()
        }
    }

    private func controlledElsewhere(deviceName: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "desktopcomputer")
                .font(.system(size: 44))
                .foregroundStyle(appTheme.secondaryForeground)

            VStack(spacing: 6) {
                Text("Controlled on \(deviceName)")
                    .font(.headline)
                    .foregroundStyle(appTheme.foreground)
                Text("This terminal is active on your Mac.")
                    .font(.subheadline)
                    .foregroundStyle(appTheme.secondaryForeground)
                    .multilineTextAlignment(.center)
            }

            Button {
                session.takeControl()
            } label: {
                Text("Take Control")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(appTheme.onAccent)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(appTheme.accent))
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(appTheme.background)
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
