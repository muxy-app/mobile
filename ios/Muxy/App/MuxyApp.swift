import SwiftUI

@main
struct MuxyApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate

    var body: some Scene {
        WindowGroup {
            RootView(container: delegate.container)
        }
    }
}
