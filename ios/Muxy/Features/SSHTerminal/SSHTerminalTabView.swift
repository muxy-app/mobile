import SwiftUI

struct SSHTerminalTabView: View {
    @Bindable var session: SSHTerminalSession

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
        switch session.state {
        case .connecting, .idle:
            ProgressView()
                .tint(appTheme.accent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(appTheme.background)
        case .disconnected:
            stateOverlay(
                title: "Disconnected",
                systemImage: "wifi.slash",
                message: "The SSH session ended."
            )
        case let .failed(error):
            stateOverlay(
                title: "Connection Failed",
                systemImage: "exclamationmark.triangle",
                message: error.message
            )
        case .connected:
            EmptyView()
        }
    }

    private func stateOverlay(title: String, systemImage: String, message: String) -> some View {
        ThemedEmptyState(title: title, systemImage: systemImage, message: message) {
            Button("Retry") { session.retry() }
                .buttonStyle(ThemedBorderedButtonStyle())
        }
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
