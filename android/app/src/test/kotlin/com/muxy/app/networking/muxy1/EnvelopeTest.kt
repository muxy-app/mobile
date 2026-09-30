package com.muxy.app.networking.muxy1

import com.muxy.app.networking.muxy1.protocol.AuthParams
import com.muxy.app.networking.muxy1.protocol.CreateTabParams
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.FrameException
import com.muxy.app.networking.muxy1.protocol.IncomingFrame
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.PairingResult
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.RequestEnvelope
import com.muxy.app.networking.muxy1.protocol.ResultType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeTest {
    @Test
    fun requestEncodesEnvelopeAndTaggedParams() {
        val params = ProtocolJson.encodeToJsonElement(AuthParams.serializer(), AuthParams("dev-1", "iPhone", "secret"))
        val root = ProtocolJson.parseToJsonElement(RequestEnvelope.encode("req-1", Method.AUTHENTICATE_DEVICE, params)).jsonObject
        assertEquals("request", root.getValue("type").jsonPrimitive.content)
        val payload = root.getValue("payload").jsonObject
        assertEquals("req-1", payload.getValue("id").jsonPrimitive.content)
        assertEquals("authenticateDevice", payload.getValue("method").jsonPrimitive.content)
        val tagged = payload.getValue("params").jsonObject
        assertEquals("authenticateDevice", tagged.getValue("type").jsonPrimitive.content)
        val value = tagged.getValue("value").jsonObject
        assertEquals("dev-1", value.getValue("deviceID").jsonPrimitive.content)
        assertEquals("iPhone", value.getValue("deviceName").jsonPrimitive.content)
        assertEquals("secret", value.getValue("token").jsonPrimitive.content)
    }

    @Test
    fun requestWithoutParamsEncodesNull() {
        val text = RequestEnvelope.encode("req-2", Method.LIST_PROJECTS, null)
        assertTrue(text.contains("\"params\":null"))
        val payload =
            ProtocolJson
                .parseToJsonElement(text)
                .jsonObject
                .getValue("payload")
                .jsonObject
        assertEquals(JsonNull, payload["params"])
    }

    @Test
    fun paramsOmitMissingOptionalsLikeSwift() {
        val params = ProtocolJson.encodeToJsonElement(CreateTabParams.serializer(), CreateTabParams("p", null, "terminal")).jsonObject
        assertFalse(params.containsKey("areaID"))
        assertEquals("p", params.getValue("projectID").jsonPrimitive.content)
    }

    @Test
    fun responseWithResultDecodes() {
        val frame =
            IncomingFrame.parse(
                """
                { "type": "response", "payload": { "id": "req-1", "result": { "type": "pairing", "value": {
                  "clientID": "client-9", "deviceName": "iPhone", "themeFg": 16777215, "themeBg": 197379, "themePalette": [0, 16711680, 65280] } } } }
                """,
            )
        val response = (frame as IncomingFrame.Response).envelope
        assertEquals("req-1", response.id)
        assertNull(response.error)
        val result = response.result!!
        assertEquals(ResultType.PAIRING, result.type)
        val pairing = result.decode(PairingResult.serializer())
        assertEquals("client-9", pairing.clientId)
        assertEquals("iPhone", pairing.deviceName)
    }

    @Test
    fun resultWithoutValueDecodesAsNull() {
        val frame = IncomingFrame.parse("""{ "type": "response", "payload": { "id": "x", "result": { "type": "ok" } } }""")
        val result = (frame as IncomingFrame.Response).envelope.result!!
        assertEquals(ResultType.OK, result.type)
        assertEquals(JsonNull, result.value)
    }

    @Test
    fun responseWithErrorDecodes() {
        val frame =
            IncomingFrame.parse(
                """{ "type": "response", "payload": { "id": "req-3", "error": { "code": 401, "message": "Authentication required" } } }""",
            )
        val response = (frame as IncomingFrame.Response).envelope
        assertNull(response.result)
        val error = response.error!!
        assertEquals(401, error.code)
        assertEquals("Authentication required", error.message)
        assertEquals(ErrorCode.UNAUTHORIZED, ProtocolException(error).code)
    }

    @Test
    fun eventFrameDecodes() {
        val frame =
            IncomingFrame.parse(
                """{ "type": "event", "payload": { "event": "projectsChanged", "data": { "type": "projects", "value": { "projects": [] } } } }""",
            )
        val event = (frame as IncomingFrame.Event).envelope
        assertEquals("projectsChanged", event.event)
        assertEquals("projects", event.data?.type)
    }

    @Test
    fun unknownFrameTypeThrows() {
        val error = assertThrows(FrameException::class.java) { IncomingFrame.parse("""{ "type": "telemetry", "payload": {} }""") }
        assertEquals("telemetry", error.type)
    }

    @Test
    fun unknownErrorCodesHaveNoErrorCode() {
        assertNull(ErrorCode.of(418))
        assertEquals(ErrorCode.PAIRING_TIMEOUT, ErrorCode.of(408))
    }
}
