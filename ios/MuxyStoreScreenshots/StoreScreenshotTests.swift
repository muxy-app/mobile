import XCTest

@MainActor
final class StoreScreenshotTests: XCTestCase {
    private let app = StoreApp()

    override func setUp() async throws {
        try StoreCamera.requireOutputDirectory()
        continueAfterFailure = false
    }

    func test_projects() throws {
        app.launch()
        if app.isSplit {
            app.openMuxyWithSidebar()
        }
        try StoreCamera.capture("projects")
    }

    func test_agent() throws {
        app.launch()
        app.openFull(StoreApp.muxyTab)
        try StoreCamera.capture("agent")
    }

    func test_editor() throws {
        try captureEditor(named: "editor", theme: "Muxy")
    }

    func test_git() throws {
        app.launch()
        app.openMuxyWithSidebar()
        app.tap("Git", settle: 2.5)
        try StoreCamera.capture("git")
    }

    func test_diff() throws {
        app.launch()
        app.openMuxyWithSidebar()
        app.tap("Git", settle: 2.5)
        app.tapScrolling("TabStore.swift, Sources/Muxy/Tabs/TabStore.swift, M")
        try StoreCamera.capture("diff")
    }

    func test_files() throws {
        app.launch()
        app.openMuxyWithSidebar()
        app.tap("Files", settle: 2.5)
        try StoreCamera.capture("files")
    }

    func test_preview() throws {
        app.launch()
        app.openMuxyWithSidebar()
        app.tap("Files", settle: 2.5)
        app.tap("Sources, Folder")
        app.tap("Muxy, Folder")
        app.tap("Tabs, Folder")
        app.tap("TabStore.swift, Swift source", settle: 2.5)
        try StoreCamera.capture("preview")
    }

    func test_dev() throws {
        app.launch()
        app.openFull("dev, Web App, Demo Desktop")
        try StoreCamera.capture("dev")
    }

    func test_theme_editor_tokyonight() throws {
        try captureEditor(named: "theme-editor-tokyonight", theme: "TokyoNight")
    }

    func test_theme_editor_gruvbox_dark() throws {
        try captureEditor(named: "theme-editor-gruvbox-dark", theme: "Gruvbox Dark")
    }

    func test_theme_editor_catppuccin_latte() throws {
        try captureEditor(named: "theme-editor-catppuccin-latte", theme: "Catppuccin Latte")
    }

    private func captureEditor(named name: String, theme: String) throws {
        app.launch(theme: theme)
        app.openFull(StoreApp.muxyTab)
        app.tap("nvim", settle: 2.5)
        try StoreCamera.capture(name)
    }
}
