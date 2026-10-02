import MuxyMobile
import Testing
@testable import Muxy

struct TerminalSelectionTests {
    private func selection(from anchor: (Int, Int), to head: (Int, Int)) -> TerminalSelection {
        TerminalSelection(
            anchor: TerminalCellPosition(row: anchor.0, column: anchor.1),
            head: TerminalCellPosition(row: head.0, column: head.1)
        )
    }

    @Test func aSingleRowSelectionCoversItsColumns() {
        #expect(selection(from: (0, 2), to: (0, 5)).columns(inRow: 0, columnCount: 80) == 2..<6)
    }

    @Test func draggingBackwardsSelectsTheSameCells() {
        #expect(selection(from: (0, 5), to: (0, 2)).columns(inRow: 0, columnCount: 80) == 2..<6)
    }

    @Test func middleRowsAreSelectedEdgeToEdge() {
        let selected = selection(from: (1, 10), to: (3, 4))
        #expect(selected.columns(inRow: 1, columnCount: 80) == 10..<80)
        #expect(selected.columns(inRow: 2, columnCount: 80) == 0..<80)
        #expect(selected.columns(inRow: 3, columnCount: 80) == 0..<5)
        #expect(selected.columns(inRow: 4, columnCount: 80) == nil)
    }

    @Test func aSelectionPastANarrowerGridSelectsNothingInsteadOfCrashing() {
        let selected = selection(from: (0, 80), to: (0, 85))
        #expect(selected.columns(inRow: 0, columnCount: 45) == nil)
    }

    @Test func aSelectionEndingPastANarrowerGridIsClamped() {
        let selected = selection(from: (0, 40), to: (0, 85))
        #expect(selected.columns(inRow: 0, columnCount: 45) == 40..<45)
    }

    @Test func copiedTextJoinsRowsAndTrimsTrailingSpaces() {
        let lines = [Fixtures.line("hello   "), Fixtures.line("world")]
        #expect(selection(from: (0, 0), to: (1, 4)).text(in: lines, columnCount: 8) == "hello\nworld")
    }

    @Test func rowsPastTheDocumentAreIgnored() {
        let lines = [Fixtures.line("only")]
        #expect(selection(from: (0, 0), to: (5, 3)).text(in: lines, columnCount: 8) == "only")
    }
}
