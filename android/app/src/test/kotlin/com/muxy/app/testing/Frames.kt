package com.muxy.app.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object Frames {
    fun payload(frame: String): JsonObject =
        Json
            .parseToJsonElement(frame)
            .jsonObject
            .getValue("payload")
            .jsonObject

    fun id(frame: String): String = payload(frame).getValue("id").jsonPrimitive.content

    fun method(frame: String): String = payload(frame).getValue("method").jsonPrimitive.content

    fun params(frame: String): JsonObject =
        payload(frame)
            .getValue("params")
            .jsonObject
            .getValue("value")
            .jsonObject

    fun pairing(
        id: String,
        clientId: String = "c",
        deviceName: String = "iPhone",
    ): String =
        """
        { "type": "response", "payload": { "id": "$id", "result": { "type": "pairing",
          "value": { "clientID": "$clientId", "deviceName": "$deviceName" } } } }
        """.trimIndent()

    fun result(
        id: String,
        type: String,
        value: String? = null,
    ): String {
        val body = value?.let { """, "value": $it""" }.orEmpty()
        return """{ "type": "response", "payload": { "id": "$id", "result": { "type": "$type"$body } } }"""
    }

    fun error(
        id: String,
        code: Int,
        message: String = "e",
    ): String = """{ "type": "response", "payload": { "id": "$id", "error": { "code": $code, "message": "$message" } } }"""

    fun event(
        name: String,
        type: String,
        value: String,
    ): String = """{ "type": "event", "payload": { "event": "$name", "data": { "type": "$type", "value": $value } } }"""

    fun authenticated(frame: String): List<String> = if (method(frame) == "authenticateDevice") listOf(pairing(id(frame))) else emptyList()

    fun project(
        id: String,
        name: String,
        sortOrder: Double = 0.0,
        workspaceId: String? = null,
        workspaceName: String? = null,
        logo: String? = null,
    ): String {
        val workspace = if (workspaceId != null) """, "workspaceID": "$workspaceId", "workspaceName": "$workspaceName"""" else ""
        val logoField = if (logo != null) """, "logo": "$logo"""" else ""
        val base = """"id": "$id", "name": "$name", "path": "/$name", "sortOrder": $sortOrder, "createdAt": "2026-04-19T10:00:00Z""""
        return "{ $base$workspace$logoField }"
    }
}
