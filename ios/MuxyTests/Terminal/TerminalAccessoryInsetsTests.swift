import CoreGraphics
import Testing
@testable import Muxy

struct TerminalAccessoryInsetsTests {
    private let bar = CGRect(x: 0, y: 700, width: 1024, height: 72)

    @Test func missingPageUsesFullWidth() {
        let insets = TerminalAccessoryInsets(page: nil, bar: bar)
        #expect(insets.leading == 0)
        #expect(insets.trailing == 0)
    }

    @Test func missingBarUsesFullWidth() {
        let insets = TerminalAccessoryInsets(page: CGRect(x: 320, y: 0, width: 704, height: 900), bar: nil)
        #expect(insets.leading == 0)
        #expect(insets.trailing == 0)
    }

    @Test func pageMatchingBarAddsNoInsets() {
        let insets = TerminalAccessoryInsets(page: CGRect(x: 0, y: 0, width: 1024, height: 900), bar: bar)
        #expect(insets.leading == 0)
        #expect(insets.trailing == 0)
    }

    @Test func detailColumnBesideSidebarInsetsLeadingEdge() {
        let insets = TerminalAccessoryInsets(page: CGRect(x: 320, y: 0, width: 704, height: 900), bar: bar)
        #expect(insets.leading == 320)
        #expect(insets.trailing == 0)
    }

    @Test func narrowerPageInsetsBothEdges() {
        let insets = TerminalAccessoryInsets(page: CGRect(x: 100, y: 0, width: 800, height: 900), bar: bar)
        #expect(insets.leading == 100)
        #expect(insets.trailing == 124)
    }

    @Test func pageWiderThanBarAddsNoInsets() {
        let insets = TerminalAccessoryInsets(page: CGRect(x: -50, y: 0, width: 1124, height: 900), bar: bar)
        #expect(insets.leading == 0)
        #expect(insets.trailing == 0)
    }
}
