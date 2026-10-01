package com.muxy.app.features.files

import com.muxy.app.core.text.Graphemes
import com.muxy.app.models.FileLimits
import com.muxy.app.models.RemoteTextFile
import com.muxy.app.testing.fileEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePreviewStateTest {
    @Test
    fun shortenedPreviewsKeepTheCompleteTextForEditing() {
        val text = "a".repeat(FileLimits.PREVIEW_CHARACTERS) + "tail"
        val preview = FilePreviewState(fileEntry("a")).showing(RemoteTextFile("a", text, text.length.toLong()))
        assertEquals(FileLimits.PREVIEW_CHARACTERS, preview.displayText.length)
        assertEquals(text, preview.draft)
        assertEquals(text, preview.text?.text)
        assertTrue(preview.isPreviewShortened)
        assertFalse(preview.isDirty)
        assertTrue(preview.copy(isEditing = true, draft = "changed").isDirty)
    }

    @Test
    fun characterLimitsDoNotSplitEmojiOrCombiningSequences() {
        val prefix = "a".repeat(FileLimits.PREVIEW_CHARACTERS - 1)
        val text = prefix + "😀" + "tail"
        val preview = FilePreviewState(fileEntry("a")).showing(RemoteTextFile("a", text, text.toByteArray().size.toLong()))
        assertEquals(prefix + "😀", preview.displayText)
        assertEquals("e\u0301", Graphemes.prefix("e\u0301x", 1))
        assertEquals("", Graphemes.prefix("abc", 0))
        assertEquals("abc", Graphemes.prefix("abc", 9))
    }

    @Test
    fun loadingNewTextClearsExternalChangesAndEditingState() {
        val preview =
            FilePreviewState(fileEntry("a"), isEditing = true, draft = "old", hasExternalChanges = true)
                .showing(RemoteTextFile("a", "new", 3))
        assertFalse(preview.isEditing)
        assertFalse(preview.hasExternalChanges)
        assertFalse(preview.isPreviewShortened)
        assertEquals("new", preview.displayText)
    }

    @Test
    fun remoteHostDeletionIsPermanentButComputerDeletionUsesTrash() {
        assertEquals("Delete permanently?", FileHost.Remote.deletionTitle)
        assertTrue(FileHost.Remote.deletionMessage("a").contains("permanently"))
        assertEquals("Move to Trash?", FileHost.Mac.deletionTitle)
        assertTrue(FileHost.Computer("Studio").deletionMessage("a").contains("Trash on Studio"))
    }
}
