import SwiftUI
import UIKit

enum NavigationBarAppearance {
    static func apply(_ theme: AppTheme, in window: UIWindow) {
        let appearance = barAppearance(for: theme)
        style(UINavigationBar.appearance(), with: appearance)
        window.navigationBars.forEach { style($0, with: appearance) }
    }

    private static func style(_ bar: UINavigationBar, with appearance: UINavigationBarAppearance) {
        bar.standardAppearance = appearance
        bar.compactAppearance = appearance
        bar.scrollEdgeAppearance = appearance
        bar.compactScrollEdgeAppearance = appearance
    }

    private static func barAppearance(for theme: AppTheme) -> UINavigationBarAppearance {
        let foreground = UIColor(theme.foreground)
        let chevron = backChevron

        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(theme.background)
        appearance.shadowColor = .clear
        appearance.titleTextAttributes = [.foregroundColor: foreground]
        appearance.largeTitleTextAttributes = [.foregroundColor: foreground]
        appearance.backButtonAppearance = buttonAppearance(titleColor: .clear)
        appearance.buttonAppearance = buttonAppearance(titleColor: foreground)
        appearance.prominentButtonAppearance = buttonAppearance(titleColor: foreground)
        appearance.setBackIndicatorImage(chevron, transitionMaskImage: chevron)
        return appearance
    }

    private static func buttonAppearance(titleColor: UIColor) -> UIBarButtonItemAppearance {
        let appearance = UIBarButtonItemAppearance(style: .plain)
        appearance.normal.titleTextAttributes = [.foregroundColor: titleColor]
        appearance.highlighted.titleTextAttributes = [.foregroundColor: titleColor]
        return appearance
    }

    private static var backChevron: UIImage? {
        UIImage(systemName: "chevron.backward")?
            .withConfiguration(UIImage.SymbolConfiguration(weight: .semibold))
            .withRenderingMode(.alwaysTemplate)
    }
}

private extension UIWindow {
    var navigationBars: [UINavigationBar] {
        guard let rootViewController else { return [] }
        return sequence(first: rootViewController, next: \.presentedViewController)
            .flatMap(\.navigationControllersInHierarchy)
            .map(\.navigationBar)
    }
}

private extension UIViewController {
    var navigationControllersInHierarchy: [UINavigationController] {
        let descendants = children.flatMap(\.navigationControllersInHierarchy)
        guard let navigationController = self as? UINavigationController else { return descendants }
        return [navigationController] + descendants
    }
}
