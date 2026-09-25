import Foundation

nonisolated enum TerminalKey: Hashable, Sendable {
    case character(String)
    case enter
    case tab
    case backTab
    case escape
    case backspace
    case insert
    case delete
    case up
    case down
    case left
    case right
    case home
    case end
    case pageUp
    case pageDown
    case function(UInt8)
}

nonisolated struct TerminalKeyModifiers: OptionSet, Hashable, Sendable {
    let rawValue: UInt8

    static let shift = TerminalKeyModifiers(rawValue: 1 << 0)
    static let alt = TerminalKeyModifiers(rawValue: 1 << 1)
    static let control = TerminalKeyModifiers(rawValue: 1 << 2)
}

nonisolated struct TerminalKeyStroke: Hashable, Sendable {
    let key: TerminalKey
    let modifiers: TerminalKeyModifiers

    init(_ key: TerminalKey, modifiers: TerminalKeyModifiers = []) {
        self.key = key
        self.modifiers = modifiers
    }
}
