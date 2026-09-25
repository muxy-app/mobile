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
}
