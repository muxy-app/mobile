package com.muxy.app.features.files

object RemoteFilePath {
    fun join(
        parent: String,
        name: String,
    ): String = if (parent.isEmpty()) name else "$parent/$name"

    fun name(path: String): String = path.substringAfterLast('/')

    fun parent(path: String): String = path.substringBeforeLast('/', "")

    fun validatedName(value: String): String {
        val name = value.trim()
        if (name.isEmpty()) throw FileException.Message("Enter a name.")
        if (name == "." || name == "..") throw FileException.Message("Choose another name.")
        if (name.any { it == '/' || it == '\\' || it == '\u0000' }) {
            throw FileException.Message("Names cannot contain path separators.")
        }
        return name
    }

    fun validate(
        path: String,
        allowRoot: Boolean = false,
    ) {
        if (path.isEmpty() && allowRoot) return
        val invalid = path.contains('\u0000') || path.split('/').any { it.isEmpty() || it == "." || it == ".." }
        if (invalid) throw FileException.Message("Choose a path within this project.")
    }

    fun contains(
        path: String,
        directory: String,
    ): Boolean = directory.isEmpty() || path == directory || path.startsWith("$directory/")

    fun affectsDirectory(
        path: String,
        directory: String,
    ): Boolean = path.isEmpty() || parent(path) == directory || contains(directory, path)

    fun extension(path: String): String {
        val name = name(path)
        if (name.lastIndexOf('.') <= 0) return ""
        return name.substringAfterLast('.').lowercase()
    }

    fun isImage(path: String): Boolean = extension(path) in imageExtensions

    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif")
}

sealed class FileException(
    message: String,
) : Exception(message) {
    class WorktreeChanged : FileException("The active worktree changed. Return to Files before continuing. Your draft is preserved.")

    class UnexpectedResponse : FileException("The server returned an unexpected file response.")

    class NotText : FileException("This file isn’t UTF-8 text.")

    class Message(
        message: String,
    ) : FileException(message)
}
