package com.muxy.app.features.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFilePathTest {
    @Test
    fun namesAreTrimmedButCannotEscapeTheFolder() {
        assertEquals("résumé.md", RemoteFilePath.validatedName("  résumé.md\n"))
        listOf("", "  ", ".", "..", "a/b", "a\\b", "a\u0000b").forEach {
            assertThrows(it, FileException.Message::class.java) { RemoteFilePath.validatedName(it) }
        }
    }

    @Test
    fun onlyRelativePathsInsideTheProjectAreAccepted() {
        listOf("", "/etc/passwd", "../secret", "a/../b", "a/./b", "a//b", "a/", "\u0000").forEach {
            assertThrows(it, FileException.Message::class.java) { RemoteFilePath.validate(it) }
        }
        RemoteFilePath.validate("", allowRoot = true)
        RemoteFilePath.validate("Sources/App.swift")
        RemoteFilePath.validate(".build/state.json")
    }

    @Test
    fun containmentUsesPathBoundaries() {
        assertTrue(RemoteFilePath.contains("a/b", "a"))
        assertTrue(RemoteFilePath.contains("a", "a"))
        assertTrue(RemoteFilePath.contains("a", ""))
        assertFalse(RemoteFilePath.contains("ab/c", "a"))
        assertFalse(RemoteFilePath.contains("a", "a/b"))
        assertEquals("a", RemoteFilePath.parent("a/b"))
        assertEquals("", RemoteFilePath.parent("a"))
        assertEquals("a/b", RemoteFilePath.join("a", "b"))
    }

    @Test
    fun changesAffectAncestorsAndImmediateChildrenNotSiblings() {
        assertTrue(RemoteFilePath.affectsDirectory("", "a/b"))
        assertTrue(RemoteFilePath.affectsDirectory("a", "a/b"))
        assertTrue(RemoteFilePath.affectsDirectory("a/b/file", "a/b"))
        assertFalse(RemoteFilePath.affectsDirectory("a/c/file", "a/b"))
    }

    @Test
    fun imageRecognitionIsCaseInsensitiveAndRequiresAnExtension() {
        listOf("a.PNG", "a.jpeg", "a.webp", "a.heic", "a.heif", "a.gif", "a.bmp").forEach {
            assertTrue(it, RemoteFilePath.isImage(it))
        }
        listOf(".png", "README", "a.txt", "images.png/README").forEach {
            assertFalse(it, RemoteFilePath.isImage(it))
        }
    }
}
