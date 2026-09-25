import SwiftUI

struct TerminalScreenView: View {
    let controller: TerminalController
    let settings: AppSettings
    let isDisconnected: Bool

    @Environment(\.appTheme) private var theme
    @State private var keyboardOffset: CGFloat = 0

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            TerminalSurfaceRepresentable(
                controller: controller,
                palette: theme.palette,
                useNerdFont: settings.useNerdFont,
                autoFocus: settings.autoFocusTerminal,
                onKeyboardOffsetChange: { keyboardOffset = $0 }
            )

            overlay

            if controller.mode == .history || !controller.isFollowing {
                jumpToLiveButton
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
        .background(theme.background)
    }

    @ViewBuilder
    private var overlay: some View {
        if isDisconnected {
            ThemedEmptyState(
                title: "Disconnected",
                systemImage: "wifi.slash",
                message: "Reconnect to continue using this terminal."
            )
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
        case .live, .ended:
            EmptyView()
        }
    }

    private var loadingState: some View {
        ProgressView()
            .tint(theme.accent)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(theme.background)
    }

    private var jumpToLiveButton: some View {
        Button(action: controller.returnToLive) {
            Image(systemName: "chevron.down")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(theme.foreground)
                .frame(width: 40, height: 40)
                .background(.regularMaterial, in: Circle())
                .overlay {
                    Circle()
                        .stroke(theme.foreground.opacity(0.18), lineWidth: 1)
                }
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .shadow(color: .black.opacity(0.18), radius: 8, y: 3)
        .padding(16)
        .accessibilityLabel("Back to Live Output")
    }

    private func noticeView(_ notice: String) -> some View {
        Text(notice)
            .font(.footnote.weight(.medium))
            .foregroundStyle(theme.foreground)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(.regularMaterial, in: Capsule())
            .padding(.top, 12)
            .transition(.move(edge: .top).combined(with: .opacity))
    }
}

private struct TerminalSurfaceRepresentable: UIViewRepresentable {
    let controller: TerminalController
    let palette: ThemePalette
    let useNerdFont: Bool
    let autoFocus: Bool
    let onKeyboardOffsetChange: (CGFloat) -> Void

    func makeUIView(context: Context) -> TerminalSurfaceView {
        let view = TerminalSurfaceView(controller: controller, theme: palette, useNerdFont: useNerdFont)
        view.onKeyboardOffsetChange = onKeyboardOffsetChange
        view.update(theme: palette, useNerdFont: useNerdFont, autoFocus: autoFocus)
        return view
    }

    func updateUIView(_ view: TerminalSurfaceView, context: Context) {
        view.onKeyboardOffsetChange = onKeyboardOffsetChange
        view.update(theme: palette, useNerdFont: useNerdFont, autoFocus: autoFocus)
    }

    static func dismantleUIView(_ view: TerminalSurfaceView, coordinator: ()) {
        view.detachFromController()
    }
}
