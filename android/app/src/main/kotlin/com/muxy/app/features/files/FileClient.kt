package com.muxy.app.features.files

import com.muxy.app.core.logging.Log
import com.muxy.app.core.text.NaturalOrder
import com.muxy.app.models.FileLimits
import com.muxy.app.models.FileScope
import com.muxy.app.models.RemoteFileEntry
import com.muxy.app.models.RemoteFileStat
import com.muxy.app.models.RemoteTextFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

class FileClient(
    val backend: FileBackend,
) {
    data class Directory(
        val path: String,
        val entries: List<RemoteFileEntry>,
    )

    suspend fun list(path: String): Directory {
        RemoteFilePath.validate(path, allowRoot = true)
        val resolved = if (path.isEmpty()) "" else directoryPath(path)
        val entries = backend.list(resolved)
        return withContext(Dispatchers.Default) {
            val seen = mutableSetOf<String>()
            entries.forEach {
                RemoteFilePath.validate(it.path)
                if (!seen.add(it.path)) throw FileException.UnexpectedResponse()
            }
            val names = NaturalOrder()
            Directory(resolved, entries.sortedWith(compareBy<RemoteFileEntry> { !it.isDirectory }.thenBy(names) { it.name }))
        }
    }

    suspend fun stat(path: String): RemoteFileStat {
        RemoteFilePath.validate(path)
        val stat = backend.stat(path)
        RemoteFilePath.validate(stat.path, allowRoot = stat.isDirectory)
        if (stat.size < 0) throw FileException.UnexpectedResponse()
        return stat
    }

    suspend fun readText(path: String): RemoteTextFile {
        requireReadable(path)
        val file = backend.readText(path)
        RemoteFilePath.validate(file.path)
        if (file.size !in 0..FileLimits.MAXIMUM_BYTES.toLong() || file.text.toByteArray().size > FileLimits.MAXIMUM_BYTES) {
            throw FileException.UnexpectedResponse()
        }
        return file
    }

    suspend fun readData(path: String): ByteArray {
        requireReadable(path)
        val data = backend.readData(path)
        if (data.size > FileLimits.MAXIMUM_BYTES) throw FileException.UnexpectedResponse()
        return data
    }

    suspend fun write(
        path: String,
        contents: String,
        scope: FileScope,
    ) {
        RemoteFilePath.validate(path)
        if (contents.toByteArray().size > FileLimits.MAXIMUM_BYTES) {
            throw FileException.Message("Files larger than 5 MiB cannot be saved from the app.")
        }
        backend.writeText(contents, path, scope)
    }

    suspend fun create(
        path: String,
        scope: FileScope,
    ): String {
        RemoteFilePath.validate(path)
        val temporary = RemoteFilePath.join(RemoteFilePath.parent(path), ".muxy-mobile-create-${UUID.randomUUID()}")
        write(temporary, "", scope)
        try {
            return rename(temporary, RemoteFilePath.name(path), scope)
        } catch (error: Exception) {
            try {
                withContext(NonCancellable) { delete(listOf(temporary), scope) }
            } catch (cleanup: Exception) {
                Log.files.error("Temporary file cleanup failed: ${cleanup.javaClass.simpleName}")
            }
            throw error
        }
    }

    suspend fun mkdir(
        path: String,
        scope: FileScope,
    ): String {
        RemoteFilePath.validate(path)
        return backend.createDirectory(path, scope).also { RemoteFilePath.validate(it) }
    }

    suspend fun rename(
        path: String,
        name: String,
        scope: FileScope,
    ): String {
        RemoteFilePath.validate(path)
        return backend.rename(path, RemoteFilePath.validatedName(name), scope).also { RemoteFilePath.validate(it) }
    }

    suspend fun move(
        paths: List<String>,
        destination: String,
        scope: FileScope,
    ) {
        validateSources(paths)
        RemoteFilePath.validate(destination, allowRoot = true)
        if (paths.any { RemoteFilePath.contains(destination, it) }) throw FileException.Message("An item cannot be moved into itself.")
        backend.move(paths, destination, scope)
    }

    suspend fun delete(
        paths: List<String>,
        scope: FileScope,
    ) {
        validateSources(paths)
        backend.delete(paths, scope)
    }

    private suspend fun directoryPath(path: String): String {
        val stat = stat(path)
        if (!stat.isDirectory) {
            throw FileException.Message("This item is no longer a folder. Return to its parent folder to refresh it.")
        }
        return stat.path
    }

    private suspend fun requireReadable(path: String) {
        val stat = stat(path)
        if (stat.isDirectory) throw FileException.Message("This item is now a folder. Return to Files to open it.")
        if (stat.size > FileLimits.MAXIMUM_BYTES) throw FileException.Message("Files larger than 5 MiB cannot be opened from the app.")
    }

    private fun validateSources(paths: List<String>) {
        if (paths.isEmpty()) throw FileException.Message("Select at least one item.")
        paths.forEach { RemoteFilePath.validate(it) }
    }
}
