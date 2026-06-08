import SwiftUI
import UIKit

@main
struct MuxyApp: App {
    @State private var container = AppContainer()

    init() {
        configureBackButtonAppearance()
    }

    var body: some Scene {
        WindowGroup {
            RootView(container: container)
        }
    }

    private func configureBackButtonAppearance() {
        let appearance = UINavigationBar.appearance()
        var backButton = UIBarButtonItemAppearance(style: .plain)
        backButton.normal.titleTextAttributes = [.foregroundColor: UIColor.clear]
        backButton.highlighted.titleTextAttributes = [.foregroundColor: UIColor.clear]

        let barAppearance = UINavigationBarAppearance()
        barAppearance.configureWithDefaultBackground()
        barAppearance.backButtonAppearance = backButton

        appearance.standardAppearance = barAppearance
        appearance.compactAppearance = barAppearance
        appearance.scrollEdgeAppearance = barAppearance
    }
}
