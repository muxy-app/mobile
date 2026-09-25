import MuxyMobile
import Testing
@testable import Muxy

@MainActor
struct HistoryDocumentTests {
    private func lines(_ texts: [String]) -> [Line] {
        texts.map(Fixtures.line)
    }

    @Test func separatesHistoryFromTheScreenRows() {
        let snapshot = StubScrollbackSnapshot(lines: lines(["h1", "h2", "s1", "s2"]), historyRows: 50, olderPages: [])
        let document = HistoryDocument(snapshot: snapshot, screenRows: 2)
        #expect(document.historyRowCount == 2)
        #expect(!document.reachedStart)
    }

    @Test func olderPagesArePrependedOldestFirst() async {
        let snapshot = StubScrollbackSnapshot(
            lines: lines(["h3", "s1"]),
            historyRows: 3,
            olderPages: [.success(lines(["h1", "h2"]))]
        )
        let document = HistoryDocument(snapshot: snapshot, screenRows: 1)
        #expect(await document.loadOlder(maxRows: 500) == 2)
        #expect(document.lines == lines(["h1", "h2", "h3", "s1"]))
        #expect(document.historyRowCount == 3)
    }

    @Test func anEmptyPageMeansTheStartWasReached() async {
        let snapshot = StubScrollbackSnapshot(lines: lines(["h1", "s1"]), historyRows: 1, olderPages: [.success([])])
        let document = HistoryDocument(snapshot: snapshot, screenRows: 1)
        #expect(await document.loadOlder(maxRows: 500) == 0)
        #expect(document.reachedStart)
        #expect(await document.loadOlder(maxRows: 500) == 0)
        #expect(snapshot.loadCount == 1)
    }

    @Test func noHistoryStartsAtTheBeginning() {
        let snapshot = StubScrollbackSnapshot(lines: lines(["s1"]), historyRows: 0, olderPages: [])
        #expect(HistoryDocument(snapshot: snapshot, screenRows: 1).reachedStart)
    }

    @Test func aFailedPageKeepsTheDocumentAndAllowsRetrying() async {
        let snapshot = StubScrollbackSnapshot(lines: lines(["h2", "s1"]), historyRows: 2, olderPages: [.failure(.Timeout), .success(lines(["h1"]))])
        let document = HistoryDocument(snapshot: snapshot, screenRows: 1)
        #expect(await document.loadOlder(maxRows: 500) == 0)
        #expect(!document.reachedStart)
        #expect(await document.loadOlder(maxRows: 500) == 1)
        #expect(document.lines == lines(["h1", "h2", "s1"]))
    }

    @Test func screenRowsNeverExceedTheSnapshot() {
        let snapshot = StubScrollbackSnapshot(lines: lines(["s1"]), historyRows: 0, olderPages: [])
        #expect(HistoryDocument(snapshot: snapshot, screenRows: 40).historyRowCount == 0)
    }
}
