package com.muxy.app.features.demo

import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

internal object DemoRequest {
    inline fun <reified T> decode(params: JsonElement?): T {
        params ?: throw invalidParams()
        return try {
            ProtocolJson.decodeFromJsonElement<T>(params)
        } catch (error: SerializationException) {
            throw invalidParams()
        } catch (error: IllegalArgumentException) {
            throw invalidParams()
        }
    }

    fun failure(message: String) = ProtocolException(ErrorCode.INTERNAL_ERROR.body(message))

    fun invalidParams() = ProtocolException(ErrorCode.INVALID_PARAMS.body("Invalid demo request"))
}
