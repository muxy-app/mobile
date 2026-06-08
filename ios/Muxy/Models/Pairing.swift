import Foundation

nonisolated struct Pairing: Equatable, Sendable {
    let clientID: String
    let deviceName: String
    let themeForeground: Int?
    let themeBackground: Int?
    let themePalette: [Int]?
}
