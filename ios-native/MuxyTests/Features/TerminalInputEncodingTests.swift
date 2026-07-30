import Testing
@testable import Muxy

struct TerminalInputEncodingTests {
    @Test func passthroughWithoutModifiers() {
        let result = TerminalInputEncoding.apply(.cmd, to: "a")
        #expect(result == "a")
    }

    @Test func controlMapsToControlCode() {
        let result = TerminalInputEncoding.apply(.ctrl, to: "c")
        #expect(result == "\u{03}")
    }

    @Test func controlOnUppercaseMapsToSameControlCode() {
        let lower = TerminalInputEncoding.apply(.ctrl, to: "c")
        let upper = TerminalInputEncoding.apply(.ctrl, to: "C")
        #expect(lower == upper)
    }

    @Test func altPrependsEscape() {
        let result = TerminalInputEncoding.apply(.alt, to: "a")
        #expect(result == "\u{1b}a")
    }

    @Test func shiftUppercasesText() {
        let result = TerminalInputEncoding.apply(.shift, to: "a")
        #expect(result == "A")
    }

    @Test func unsupportedControlInputReturnsNil() {
        let result = TerminalInputEncoding.apply(.ctrl, to: "ab")
        #expect(result == nil)
    }

    @Test func arrowNormalMode() {
        #expect(TerminalInputEncoding.arrow(.up, applicationCursor: false) == [0x1b, 0x5b, 0x41])
        #expect(TerminalInputEncoding.arrow(.down, applicationCursor: false) == [0x1b, 0x5b, 0x42])
        #expect(TerminalInputEncoding.arrow(.right, applicationCursor: false) == [0x1b, 0x5b, 0x43])
        #expect(TerminalInputEncoding.arrow(.left, applicationCursor: false) == [0x1b, 0x5b, 0x44])
    }

    @Test func arrowApplicationCursorMode() {
        #expect(TerminalInputEncoding.arrow(.up, applicationCursor: true) == [0x1b, 0x4f, 0x41])
        #expect(TerminalInputEncoding.arrow(.left, applicationCursor: true) == [0x1b, 0x4f, 0x44])
    }
}
