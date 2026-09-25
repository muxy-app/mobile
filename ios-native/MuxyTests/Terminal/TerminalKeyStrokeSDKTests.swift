import MuxyMobile
import Testing
@testable import Muxy

struct TerminalKeyStrokeSDKTests {
    @Test func controlCharactersKeepTheirModifier() {
        let stroke = TerminalKeyStroke(.character("c"), modifiers: .control)
        #expect(stroke.sdkKey == .character(text: "c"))
        #expect(stroke.sdkModifiers == Modifiers(shift: false, alt: false, control: true))
    }

    @Test func specialKeysMapOneToOne() {
        #expect(TerminalKeyStroke(.backTab).sdkKey == .backTab)
        #expect(TerminalKeyStroke(.pageUp).sdkKey == .pageUp)
        #expect(TerminalKeyStroke(.function(7)).sdkKey == .function(number: 7))
    }

    @Test func combinedModifiersAreAllSent() {
        let stroke = TerminalKeyStroke(.left, modifiers: [.shift, .alt, .control])
        #expect(stroke.sdkModifiers == Modifiers(shift: true, alt: true, control: true))
    }
}
