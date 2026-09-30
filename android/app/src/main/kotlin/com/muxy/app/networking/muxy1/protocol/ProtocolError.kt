package com.muxy.app.networking.muxy1.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ProtocolErrorBody(
    val code: Int,
    val message: String,
)

enum class ErrorCode(
    val code: Int,
) {
    INVALID_PARAMS(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    PAIRING_TIMEOUT(408),
    INTERNAL_ERROR(500),
    ;

    fun body(message: String): ProtocolErrorBody = ProtocolErrorBody(code, message)

    companion object {
        fun of(code: Int): ErrorCode? = entries.firstOrNull { it.code == code }
    }
}

class ProtocolException(
    val body: ProtocolErrorBody,
) : Exception("Muxy error ${body.code}: ${body.message}") {
    val code: ErrorCode?
        get() = ErrorCode.of(body.code)
}
