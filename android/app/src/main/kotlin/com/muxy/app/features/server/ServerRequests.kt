package com.muxy.app.features.server

import com.muxy.app.networking.server.ServerConnection
import com.muxy.app.networking.server.ServerGitRepository
import com.muxy.app.networking.server.ServerProjectFiles
import com.muxy.app.networking.server.ServerRequestError
import uniffi.muxy_mobile.MobileException

suspend fun <T> ServerController.withFiles(
    projectId: String,
    call: suspend (ServerProjectFiles) -> T,
): T = request { current -> current.files(projectId).use { call(it) } }

suspend fun <T> ServerController.withGit(
    projectId: String,
    call: suspend (ServerGitRepository) -> T,
): T = request { current -> current.git(projectId).use { call(it) } }

private suspend fun <T> ServerController.request(call: suspend (ServerConnection) -> T): T {
    try {
        val current = connection ?: throw MobileException.Disconnected()
        val result = call(current)
        if (current !== connection) throw MobileException.Disconnected()
        return result
    } catch (error: Exception) {
        throw ServerRequestError.wrapping(error, serverName)
    }
}
