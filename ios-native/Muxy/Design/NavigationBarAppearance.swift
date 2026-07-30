import SwiftUI
import UIKit

enum NavigationBarAppearance {
    static func apply(_ theme: AppTheme) {
        let background = UIColor(theme.background)
        let foreground = UIColor(theme.foreground)

        var backButton = UIBarButtonItemAppearance(style: .plain)
        backButton.normal.titleTextAttributes = [.foregroundColor: UIColor.clear]
        backButton.highlighted.titleTextAttributes = [.foregroundColor: UIColor.clear]

        var button = UIBarButtonItemAppearance(style: .plain)
        button.normal.titleTextAttributes = [.foregroundColor: foreground]
        button.highlighted.titleTextAttributes = [.foregroundColor: foreground]

        let chevron = backChevron

        let barAppearance = UINavigationBarAppearance()
        barAppearance.configureWithOpaqueBackground()
        barAppearance.backgroundColor = background
        barAppearance.shadowColor = .clear
        barAppearance.titleTextAttributes = [.foregroundColor: foreground]
        barAppearance.largeTitleTextAttributes = [.foregroundColor: foreground]
        barAppearance.backButtonAppearance = backButton
        barAppearance.buttonAppearance = button
        barAppearance.doneButtonAppearance = button
        barAppearance.setBackIndicatorImage(chevron, transitionMaskImage: chevron)

        let appearance = UINavigationBar.appearance()
        appearance.standardAppearance = barAppearance
        appearance.compactAppearance = barAppearance
        appearance.scrollEdgeAppearance = barAppearance
        appearance.tintColor = foreground
    }

    private static var backChevron: UIImage? {
        UIImage(systemName: "chevron.backward")?
            .withConfiguration(UIImage.SymbolConfiguration(weight: .semibold))
            .withRenderingMode(.alwaysTemplate)
    }
}
