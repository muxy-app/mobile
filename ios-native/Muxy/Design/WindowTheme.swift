import SwiftUI
import UIKit

extension View {
    func themedWindow(_ theme: AppTheme) -> some View {
        background(WindowThemeApplier(theme: theme))
    }
}

private struct WindowThemeApplier: UIViewRepresentable {
    let theme: AppTheme

    func makeUIView(context: Context) -> WindowThemeView {
        WindowThemeView(theme: theme)
    }

    func updateUIView(_ view: WindowThemeView, context: Context) {
        view.theme = theme
    }
}

private final class WindowThemeView: UIView {
    var theme: AppTheme {
        didSet {
            guard theme != oldValue else { return }
            crossfadeToTheme()
        }
    }

    init(theme: AppTheme) {
        self.theme = theme
        super.init(frame: .zero)
        isUserInteractionEnabled = false
        backgroundColor = .clear
    }

    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        applyTheme()
    }

    private func crossfadeToTheme() {
        guard let window else { return }
        UIView.transition(
            with: window,
            duration: Self.crossfadeDuration,
            options: [.transitionCrossDissolve, .allowUserInteraction]
        ) {
            self.applyTheme()
        }
    }

    private func applyTheme() {
        guard let window else { return }
        let background = UIColor(theme.background)
        window.overrideUserInterfaceStyle = theme.isDark ? .dark : .light
        window.backgroundColor = background
        window.rootViewController?.view.backgroundColor = background
        NavigationBarAppearance.apply(theme, in: window)
    }

    private static let crossfadeDuration: TimeInterval = 0.25
}
