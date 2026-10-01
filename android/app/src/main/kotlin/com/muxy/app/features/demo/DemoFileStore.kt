package com.muxy.app.features.demo

import com.muxy.app.features.files.RemoteFilePath
import com.muxy.app.models.FileEncoding
import com.muxy.app.models.FileLimits
import com.muxy.app.models.RemoteFileContent
import com.muxy.app.models.RemoteFileEntry
import com.muxy.app.models.RemoteFileStat
import java.nio.charset.CharacterCodingException
import java.util.Base64

class DemoFileStore(
    projectName: String,
) {
    private data class Node(
        val isDirectory: Boolean,
        val data: ByteArray = byteArrayOf(),
        val isIgnored: Boolean = false,
    )

    private val nodes =
        mutableMapOf(
            "" to Node(true),
            "Sources" to Node(true),
            "Sources/App.swift" to
                text(
                    "import SwiftUI\n\nstruct HomeView: View {\n    var body: some View {\n        Text(\"Welcome to $projectName\")\n    }\n}\n",
                ),
            "TerminalKeyboardVisibilityConfiguration.swift" to
                text(
                    """
                    import Foundation

                    struct TerminalKeyboardVisibilityConfiguration {
                        let preservesTerminalGrid: Bool
                        let followsCursorWhenKeyboardOpens: Bool
                        let allowsManualViewportScrolling: Bool
                    }

                    let configuration = TerminalKeyboardVisibilityConfiguration(preservesTerminalGrid: true, followsCursorWhenKeyboardOpens: true, allowsManualViewportScrolling: true)

                    """.trimIndent(),
                ),
            "README.md" to
                text(
                    """
                    # $projectName

                    Browse project files, make a quick edit, and return to your terminal. Files are read from the active worktree, so you can keep working wherever you are.

                    ## Working with files

                    Tap a folder to open it. Touch and hold a file to select it, then use the actions below to rename, move, or delete it.

                    ## Reading and editing

                    Long lines wrap to fit the screen. Use the wrapping control to keep code on one line, or tap Edit file to make changes. Your edits are saved only when you choose Save changes.

                    """.trimIndent(),
                ),
            "assets" to Node(true),
            "assets/icon.png" to Node(false, Base64.getDecoder().decode(ICON)),
            "archive.bin" to Node(false, byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0, 1)),
            ".build" to Node(true, isIgnored = true),
            ".build/state.json" to Node(false, "{}\n".toByteArray(), isIgnored = true),
        )
    var changedPaths: List<String> = emptyList()
        private set

    fun resetChanges() {
        changedPaths = emptyList()
    }

    fun list(path: String): List<RemoteFileEntry> {
        val directory = normalized(path, allowRoot = true)
        requireDirectory(directory)
        return nodes
            .filter { (key, _) ->
                key.isNotEmpty() && RemoteFilePath.parent(key) == directory && RemoteFilePath.name(key) != ".git"
            }.map { (key, node) -> RemoteFileEntry(RemoteFilePath.name(key), key, node.isDirectory, node.isIgnored) }
    }

    fun stat(path: String): RemoteFileStat {
        val key = normalized(path, allowRoot = true)
        val node = node(key)
        return RemoteFileStat(RemoteFilePath.name(key), key, node.isDirectory, node.data.size.toLong())
    }

    fun read(
        path: String,
        encoding: FileEncoding,
    ): RemoteFileContent {
        val key = normalized(path)
        val node = node(key)
        if (node.isDirectory) throw DemoRequest.failure("This item is a folder.")
        val content =
            when (encoding) {
                FileEncoding.BASE64 -> {
                    Base64.getEncoder().encodeToString(node.data)
                }

                FileEncoding.UTF8 -> {
                    try {
                        node.data.decodeToString(throwOnInvalidSequence = true)
                    } catch (error: CharacterCodingException) {
                        throw DemoRequest.failure("This file is not valid UTF-8.")
                    }
                }
            }
        return RemoteFileContent(key, content, node.data.size.toLong(), encoding)
    }

    fun write(
        path: String,
        contents: String,
        encoding: FileEncoding,
    ): List<String> {
        val key = normalized(path)
        requireDirectory(RemoteFilePath.parent(key))
        if (nodes[key]?.isDirectory == true) throw DemoRequest.failure("This item is a folder.")
        val bytes =
            when (encoding) {
                FileEncoding.UTF8 -> {
                    contents.toByteArray()
                }

                FileEncoding.BASE64 -> {
                    try {
                        Base64.getDecoder().decode(contents)
                    } catch (error: IllegalArgumentException) {
                        throw DemoRequest.failure("Invalid Base64 contents.")
                    }
                }
            }
        if (bytes.size > FileLimits.MAXIMUM_BYTES) throw DemoRequest.failure("File exceeds the 5 MiB write limit.")
        nodes[key] = Node(false, bytes, nodes[key]?.isIgnored == true)
        changedPaths = listOf(key)
        return listOf(key)
    }

    fun mkdir(path: String): List<String> {
        val requested = normalized(path)
        requireDirectory(RemoteFilePath.parent(requested))
        val destination = uniquePath(requested)
        nodes[destination] = Node(true)
        changedPaths = listOf(destination)
        return listOf(destination)
    }

    fun rename(
        path: String,
        name: String,
    ): List<String> {
        val source = normalized(path)
        val valid = RemoteFilePath.validatedName(name)
        val destination = RemoteFilePath.join(RemoteFilePath.parent(source), valid)
        node(source)
        if (source == destination) return listOf(source)
        if (nodes.containsKey(destination)) throw DemoRequest.failure("“$valid” already exists in this folder.")
        relocate(source, destination)
        changedPaths = listOf(source, destination)
        return listOf(destination)
    }

    fun move(
        paths: List<String>,
        into: String,
    ): List<String> {
        val directory = normalized(into, allowRoot = true)
        requireDirectory(directory)
        val sources = paths.map { normalized(it).also(::node) }.distinct()
        if (sources.isEmpty()) throw DemoRequest.failure("Select at least one item.")
        if (sources.any { RemoteFilePath.contains(directory, it) }) throw DemoRequest.failure("An item cannot be moved into itself.")
        if (sources.any { source -> sources.any { it != source && RemoteFilePath.contains(source, it) } }) {
            throw DemoRequest.failure("Select a folder or its contents, not both.")
        }
        val changes = mutableListOf<String>()
        val destinations =
            sources.map { source ->
                if (RemoteFilePath.parent(source) == directory) return@map source
                val destination = uniquePath(RemoteFilePath.join(directory, RemoteFilePath.name(source)))
                relocate(source, destination)
                changes += listOf(source, destination)
                destination
            }
        changedPaths = changes
        return destinations
    }

    fun delete(paths: List<String>) {
        val sources = paths.map { normalized(it).also(::node) }
        if (sources.isEmpty()) throw DemoRequest.failure("Select at least one item.")
        nodes.keys.removeAll { key -> sources.any { RemoteFilePath.contains(key, it) } }
        changedPaths = sources
    }

    private fun node(path: String): Node = nodes[path] ?: throw DemoRequest.failure("“${RemoteFilePath.name(path)}” was not found.")

    private fun requireDirectory(path: String) {
        if (nodes[path]?.isDirectory != true) throw DemoRequest.failure("The folder was not found.")
    }

    private fun normalized(
        path: String,
        allowRoot: Boolean = false,
    ): String =
        (if (path == ".") "" else path).also {
            RemoteFilePath.validate(it, allowRoot)
        }

    private fun uniquePath(requested: String): String {
        val name = RemoteFilePath.name(requested)
        val extension = name.substringAfterLast('.', "").takeIf { name.lastIndexOf('.') > 0 }.orEmpty()
        val stem = if (extension.isEmpty()) name else name.dropLast(extension.length + 1)
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var candidate = requested
        var index = 2
        while (nodes.containsKey(candidate)) {
            candidate = RemoteFilePath.join(RemoteFilePath.parent(requested), "$stem ${index++}$suffix")
        }
        return candidate
    }

    private fun relocate(
        source: String,
        destination: String,
    ) {
        val affected = nodes.filterKeys { RemoteFilePath.contains(it, source) }
        affected.forEach { (path, node) ->
            nodes[destination + path.removePrefix(source)] = node
            nodes.remove(path)
        }
    }

    private companion object {
        const val ICON = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="

        fun text(value: String) = Node(false, value.toByteArray())
    }
}
