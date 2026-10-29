import SwiftUI

struct TerminalScreenView: View {
    let controller: TerminalController
    let settings: AppSettings
    let isDisconnected: Bool

    @Environment(\.appTheme) private var theme
    @Environment(\.isActiveTabPage) private var isActive
    @State private var keyboardOffset: CGFloat = 0

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            TerminalSurfaceRepresentable(
                controller: controller,
                palette: theme.terminalPalette,
                useNerdFont: settings.useNerdFont,
                autoFocus: settings.autoFocusTerminal,
                isActive: isActive,
                onKeyboardOffsetChange: { keyboardOffset = $0 }
            )
            .padding(TerminalViewportState.contentInset)

            overlay

            if controller.mode == .history || !controller.isFollowing {
                JumpToLiveButton(action: controller.returnToLive)
                    .padding(PageMargin.horizontal)
                    .padding(.bottom, keyboardOffset)
            }
        }
        .overlay(alignment: .top) {
            VStack(spacing: 8) {
                if controller.showsHistoryStart {
                    noticeView("Start of history")
                }
                if let notice = controller.notice {
                    noticeView(notice)
                }
            }
            .allowsHitTesting(false)
        }
        .keyboardIsland(bottomInset: keyboardOffset, background: ThemeColor(rgb: theme.terminalPalette.background).color)
    }

    @ViewBuilder
    private var overlay: some View {
        if isDisconnected {
            ThemedEmptyState(
                title: "Disconnected",
                systemImage: "wifi.slash",
                message: "Reconnect to continue using this terminal."
            )
            .background(theme.groupedBackground)
        } else {
            phaseOverlay
        }
    }

    @ViewBuilder
    private var phaseOverlay: some View {
        switch controller.phase {
        case .waiting, .creating, .attaching:
            if controller.cachedScreen == nil {
                loadingState
            }
        case let .failed(message):
            ThemedEmptyState(title: "Terminal Unavailable", systemImage: "exclamationmark.triangle", message: message) {
                Button("Try Again", action: controller.retry)
                    .buttonStyle(ThemedBorderedButtonStyle())
            }
            .background(theme.groupedBackground)
        case .live, .ended:
            EmptyView()
        }
    }

    private var loadingState: some View {
        ProgressView()
            .tint(theme.accent)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(theme.groupedBackground)
    }

    private func noticeView(_ text: String) -> some View {
        TerminalNotice(text: text)
            .padding(.top, 12)
            .transition(.move(edge: .top).combined(with: .opacity))
    }
}

private struct TerminalSurfaceRepresentable: UIViewRepresentable {
    let controller: TerminalController
    let palette: ThemePalette
    let useNerdFont: Bool
    let autoFocus: Bool
    let isActive: Bool
    let onKeyboardOffsetChange: (CGFloat) -> Void

    func makeUIView(context: Context) -> TerminalSurfaceView {
        let view = TerminalSurfaceView(controller: controller, theme: palette, useNerdFont: useNerdFont)
        view.onKeyboardOffsetChange = onKeyboardOffsetChange
        view.update(theme: palette, useNerdFont: useNerdFont, autoFocus: autoFocus, isActive: isActive)
        return view
    }

    func updateUIView(_ view: TerminalSurfaceView, context: Context) {
        view.onKeyboardOffsetChange = onKeyboardOffsetChange
        view.update(theme: palette, useNerdFont: useNerdFont, autoFocus: autoFocus, isActive: isActive)
    }

    static func dismantleUIView(_ view: TerminalSurfaceView, coordinator: ()) {
        view.detachFromController()
    }
}
