import XCTest

@MainActor
struct StoreApp {
    enum Sidebar {
        case shown
        case hidden
    }

    static let muxyTab = "agent, Muxy, Demo Desktop"

    private let app = XCUIApplication()

    func launch(theme: String = "Muxy") {
        app.terminate()
        app.launchArguments = [
            "-muxy.hasCompletedOnboarding", "YES",
            "-muxy.settings.demoMode", "YES",
            "-muxy.settings.theme", theme
        ]
        app.launch()
        settle(2.5)
    }

    func tap(_ label: String, settle seconds: TimeInterval = 1.5) {
        guard let element = hittable(label, timeout: 8) else {
            XCTFail("Could not find '\(label)' on screen")
            return
        }
        element.tap()
        settle(seconds)
    }

    func tapScrolling(_ label: String, settle seconds: TimeInterval = 2.5) {
        for _ in 0..<5 {
            if let element = hittable(label, timeout: 2) {
                element.tap()
                settle(seconds)
                return
            }
            app.swipeUp()
            settle(1)
        }
        XCTFail("Could not find '\(label)' after scrolling")
    }

    var isSplit: Bool {
        exists("Hide sidebar") || exists("Show sidebar")
    }

    func sidebar(_ state: Sidebar) {
        let toggle = state == .hidden ? "Hide sidebar" : "Show sidebar"
        guard exists(toggle) else { return }
        tap(toggle)
    }

    func openTab(_ label: String) {
        tap(label, settle: 3)
    }

    func openFull(_ label: String) {
        openTab(label)
        sidebar(.hidden)
    }

    func openMuxyWithSidebar() {
        openTab(Self.muxyTab)
        sidebar(.shown)
    }

    private func elements(_ label: String) -> XCUIElementQuery {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label))
    }

    private func exists(_ label: String) -> Bool {
        elements(label).firstMatch.exists
    }

    private func hittable(_ label: String, timeout: TimeInterval) -> XCUIElement? {
        let query = elements(label)
        guard query.firstMatch.waitForExistence(timeout: timeout) else { return nil }
        return query.allElementsBoundByIndex.first { $0.isHittable }
    }

    private func settle(_ seconds: TimeInterval) {
        Thread.sleep(forTimeInterval: seconds)
    }
}
