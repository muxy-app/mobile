import Foundation

extension Tab: TabStripItem {
    var systemImage: String {
        switch kind {
        case .terminal:
            return "terminal"
        case .vcs:
            return "arrow.triangle.branch"
        case .unsupported:
            return "questionmark.circle"
        }
    }

    var tileKind: TabTileKind {
        switch kind {
        case .terminal: .shell
        case .vcs: .git
        case .unsupported: .other
        }
    }

    var activity: TabActivity {
        .none
    }
}
