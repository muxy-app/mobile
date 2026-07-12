import Foundation
import Observation

@MainActor
@Observable
final class AppSettings {
    var useNerdFont: Bool {
        didSet { defaults.set(useNerdFont, forKey: Key.useNerdFont) }
    }

    var autoFocusTerminal: Bool {
        didSet { defaults.set(autoFocusTerminal, forKey: Key.autoFocusTerminal) }
    }

    var demoMode: Bool {
        didSet { defaults.set(demoMode, forKey: Key.demoMode) }
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        useNerdFont = AppSettings.storedUseNerdFont(defaults: defaults)
        autoFocusTerminal = defaults.bool(forKey: Key.autoFocusTerminal)
        demoMode = defaults.bool(forKey: Key.demoMode)
    }

    static func storedUseNerdFont(defaults: UserDefaults = .standard) -> Bool {
        defaults.object(forKey: Key.useNerdFont) as? Bool ?? true
    }
}

enum AppSettingKey {
    static let useNerdFont = "muxy.settings.useNerdFont"
    static let autoFocusTerminal = "muxy.settings.autoFocusTerminal"
    static let demoMode = "muxy.settings.demoMode"
}

private typealias Key = AppSettingKey
