package com.muxy.app.networking.server

import kotlinx.coroutines.CancellationException
import uniffi.muxy_mobile.MobileException

class ServerRequestError(
    val failure: ServerFailure,
    serverName: String,
    cause: Throwable,
) : Exception(failure.message(ServerFailure.Context.REQUEST, serverName), cause) {
    companion object {
        fun wrapping(
            error: Exception,
            serverName: String,
        ): Exception {
            if (error is CancellationException) throw error
            if (error !is MobileException) return error
            return ServerRequestError(ServerFailure.from(error), serverName, error)
        }
    }
}
