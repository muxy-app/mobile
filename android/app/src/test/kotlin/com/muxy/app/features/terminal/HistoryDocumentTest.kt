package com.muxy.app.features.terminal

import com.muxy.app.testing.StubScrollback
import com.muxy.app.testing.lines
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HistoryDocumentTest {
    @Test
    fun separatesHistoryFromTheScreenRows() {
        val document = HistoryDocument(StubScrollback(lines("h1", "h2", "s1", "s2"), 50, emptyList()), screenRows = 2)
        assertEquals(2, document.historyRowCount)
        assertFalse(document.reachedStart)
    }

    @Test
    fun olderPagesArePrependedOldestFirst() =
        runTest {
            val snapshot = StubScrollback(lines("h3", "s1"), 3, listOf(Result.success(lines("h1", "h2"))))
            val document = HistoryDocument(snapshot, screenRows = 1)
            assertEquals(2, document.loadOlder(500))
            assertEquals(lines("h1", "h2", "h3", "s1"), document.lines)
            assertEquals(3, document.historyRowCount)
        }

    @Test
    fun anEmptyPageMeansTheStartWasReached() =
        runTest {
            val snapshot = StubScrollback(lines("h1", "s1"), 1, listOf(Result.success(emptyList())))
            val document = HistoryDocument(snapshot, screenRows = 1)
            assertEquals(0, document.loadOlder(500))
            assertTrue(document.reachedStart)
            assertEquals(0, document.loadOlder(500))
            assertEquals(1, snapshot.loadCount)
        }

    @Test
    fun noHistoryStartsAtTheBeginning() {
        assertTrue(HistoryDocument(StubScrollback(lines("s1"), 0, emptyList()), screenRows = 1).reachedStart)
    }

    @Test
    fun aFailedPageKeepsTheDocumentAndAllowsRetrying() =
        runTest {
            val snapshot = StubScrollback(lines("h2", "s1"), 2, listOf(Result.failure(IOException("timeout")), Result.success(lines("h1"))))
            val document = HistoryDocument(snapshot, screenRows = 1)
            assertEquals(0, document.loadOlder(500))
            assertFalse(document.reachedStart)
            assertEquals(1, document.loadOlder(500))
            assertEquals(lines("h1", "h2", "s1"), document.lines)
        }

    @Test
    fun screenRowsNeverExceedTheSnapshot() {
        assertEquals(0, HistoryDocument(StubScrollback(lines("s1"), 0, emptyList()), screenRows = 40).historyRowCount)
    }
}
