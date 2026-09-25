import MuxyMobile
import Testing
@testable import Muxy

struct TerminalLineCellsTests {
    private let line = Line(spans: [
        Span(text: "ab ", width: 3, style: Fixtures.style()),
        Span(text: "你", width: 2, style: Fixtures.style { $0.bold = true }),
        Span(text: "cd", width: 2, style: Fixtures.style()),
    ])

    @Test func asciiCellsHoldOneCharacter() {
        #expect(TerminalLineCells.cell(at: 1, in: line)?.text == "b")
        #expect(TerminalLineCells.cell(at: 1, in: line)?.width == 1)
    }

    @Test func wideCharactersCoverBothCells() {
        let first = TerminalLineCells.cell(at: 3, in: line)
        let second = TerminalLineCells.cell(at: 4, in: line)
        #expect(first?.text == "你")
        #expect(first?.width == 2)
        #expect(second == first)
        #expect(first?.style.bold == true)
    }

    @Test func cellsPastTheEndOfTheLineAreEmpty() {
        #expect(TerminalLineCells.cell(at: 7, in: line) == nil)
    }

    @Test func textOfAColumnRangeIncludesPartlyCoveredWideCharacters() {
        #expect(TerminalLineCells.text(of: line, columns: 1..<4) == "b 你")
        #expect(TerminalLineCells.text(of: line, columns: 4..<7) == "你cd")
    }

    @Test func textOfAnEmptyRangeIsEmpty() {
        #expect(TerminalLineCells.text(of: line, columns: 9..<12).isEmpty)
    }
}
