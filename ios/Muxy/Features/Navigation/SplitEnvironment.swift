import SwiftUI

struct ActiveTabReporter {
    var select: (UUID) -> Void = { _ in }
    var clear: (UUID) -> Void = { _ in }
}

extension EnvironmentValues {
    @Entry var activeTabReporter = ActiveTabReporter()
    @Entry var activeTabID: UUID?
    @Entry var isSplitSidebar = false
}
