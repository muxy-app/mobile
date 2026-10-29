import SwiftUI

struct TerminalTabView: View {
    @Bindable var session: TerminalSession

    @Environment(\.appTheme) private var appTheme
    @Environment(\.isActiveTabPage) private var isActive
    @AppStorage(AppSettingKey.autoFocusTerminal) private var autoFocusTerminal = false
    @State private var useNerdFont = AppSettings.storedUseNerdFont()
    @State private var fontSize = TerminalFont.defaultSize
    @State private var keyboardOffset: CGFloat = 0

    var body: some View {
        terminalSurface
            .onAppear { session.useClientTheme(ClientTerminalTheme(palette: appTheme.terminalPalette)) }
            .onChange(of: appTheme) { _, newValue in
                session.useClientTheme(ClientTerminalTheme(palette: newValue.terminalPalette))
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
                autoFocusTerminal: autoFocusTerminal,
                isActive: isActive,
                onKeyboardOffsetChange: { keyboardOffset = $0 }
            )
            .padding(TerminalViewportState.contentInset)

            overlay

            if !session.isFollowingBottom {
                JumpToLiveButton(action: session.requestJumpToBottom)
                    .padding(PageMargin.horizontal)
                    .padding(.bottom, keyboardOffset)
            }
        }
        .keyboardIsland(bottomInset: keyboardOffset, background: session.theme.background.asColor)
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
            .background(appTheme.groupedBackground)
        case .idle, .takingOver, .owned:
            EmptyView()
        }
    }

    private func controlledElsewhere(deviceName: String) -> some View {
        ThemedEmptyState(
            title: "Controlled on \(deviceName)",
            systemImage: "desktopcomputer",
            message: "This terminal is active on your Mac."
        ) {
            Button("Take Control", action: session.takeControl)
                .buttonStyle(ThemedProminentButtonStyle())
        }
        .background(appTheme.groupedBackground)
    }
}

private extension UIColor {
    var asColor: Color { Color(self) }
}
