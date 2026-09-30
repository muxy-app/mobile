package com.muxy.app.core.logging

import android.util.Log as AndroidLog

class LogCategory internal constructor(
    name: String,
) {
    private val tag = "Muxy/$name"

    fun debug(message: String) {
        AndroidLog.d(tag, message)
    }

    fun info(message: String) {
        AndroidLog.i(tag, message)
    }

    fun error(
        message: String,
        error: Throwable? = null,
    ) {
        AndroidLog.e(tag, message, error)
    }
}

object Log {
    val transport = LogCategory("transport")
    val client = LogCategory("client")
    val pairing = LogCategory("pairing")
    val connection = LogCategory("connection")
    val discovery = LogCategory("discovery")
    val persistence = LogCategory("persistence")
    val terminal = LogCategory("terminal")
    val ssh = LogCategory("ssh")
    val files = LogCategory("files")
    val billing = LogCategory("billing")
}
