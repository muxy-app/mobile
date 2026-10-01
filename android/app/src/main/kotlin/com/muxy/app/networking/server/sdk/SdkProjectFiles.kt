package com.muxy.app.networking.server.sdk

import com.muxy.app.networking.server.ServerProjectFiles
import uniffi.muxy_mobile.ProjectFiles

class SdkProjectFiles(
    private val files: ProjectFiles,
    private val lanes: SdkLanes,
) : ServerProjectFiles {
    override suspend fun list(path: String) = lanes.fileRequest { files.list(path) }

    override suspend fun stat(path: String) = lanes.fileRequest { files.stat(path) }

    override suspend fun readText(path: String) = lanes.fileRequest { files.readText(path) }

    override suspend fun readBytes(path: String) = lanes.fileRequest { files.readBytes(path) }

    override suspend fun writeText(
        path: String,
        text: String,
    ) = lanes.fileRequest { files.writeText(path, text) }

    override suspend fun createDirectory(path: String) = lanes.fileRequest { files.createDirectory(path) }

    override suspend fun rename(
        path: String,
        name: String,
    ) = lanes.fileRequest { files.rename(path, name) }

    override suspend fun moveFiles(
        paths: List<String>,
        into: String,
    ) = lanes.fileRequest { files.moveFiles(paths, into) }

    override suspend fun deleteFiles(paths: List<String>) = lanes.fileRequest { files.deleteFiles(paths) }

    override suspend fun watch() = lanes.fileRequest { files.watch() }

    override suspend fun unwatch() = lanes.fileRequest { files.unwatch() }

    override fun close() = files.close()
}
