package com.muxy.app.core.concurrency

import kotlinx.coroutines.CancellationException

suspend inline fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
