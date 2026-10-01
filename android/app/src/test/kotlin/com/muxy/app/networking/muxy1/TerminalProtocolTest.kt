package com.muxy.app.networking.muxy1

import com.muxy.app.core.serialization.uuidString
import com.muxy.app.design.ThemeCatalog
import com.muxy.app.features.projectdetail.terminal.clientTerminalTheme
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.EventType
import com.muxy.app.networking.muxy1.protocol.IncomingFrame
import com.muxy.app.networking.muxy1.protocol.PaneOwner
import com.muxy.app.networking.muxy1.protocol.PaneOwnershipEvent
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.SetClientThemeParams
import com.muxy.app.networking.muxy1.protocol.TakeOverPaneParams
import com.muxy.app.networking.muxy1.protocol.TerminalBytesEvent
import com.muxy.app.networking.muxy1.protocol.TerminalInputParams
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64
import java.util.UUID

class TerminalProtocolTest {
    private fun event(json: String) = (IncomingFrame.parse(json) as IncomingFrame.Event).envelope

    @Test
    fun terminalOutputEventDecodesBase64Bytes() {
        val paneId = UUID.randomUUID()
        val bytes = byteArrayOf(0x1b, 0x5b, 0x33, 0x31, 0x6d)
        val envelope =
            event(
                """{ "type": "event", "payload": { "event": "terminalOutput", "data": { "type": "terminalOutput",
                "value": { "paneID": "${paneId.uuidString}", "bytes": "${Base64.getEncoder().encodeToString(bytes)}" } } } }""",
            )
        assertEquals(EventName.TERMINAL_OUTPUT, envelope.event)
        val data = checkNotNull(envelope.data)
        assertEquals(EventType.TERMINAL_OUTPUT, data.type)
        assertEquals(paneId, TerminalBytesEvent.paneId(data))
        val payload = data.decode(TerminalBytesEvent.serializer())
        assertEquals(paneId, payload.paneId)
        assertArrayEquals(bytes, payload.bytes)
    }

    @Test
    fun paneOwnershipDecodesRemoteAndMacOwners() {
        val paneId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        val remote =
            event(
                """{ "type": "event", "payload": { "event": "paneOwnershipChanged", "data": { "type": "paneOwnership",
                "value": { "paneID": "${paneId.uuidString}", "owner": { "remote": { "deviceID": "${deviceId.uuidString}", "deviceName": "iPad" } } } } } }""",
            )
        val mac =
            event(
                """{ "type": "event", "payload": { "event": "paneOwnershipChanged", "data": { "type": "paneOwnership",
                "value": { "paneID": "${paneId.uuidString}", "owner": { "mac": { "deviceName": "MacBook" } } } } } }""",
            )
        assertEquals(PaneOwner.Remote(deviceId, "iPad"), checkNotNull(remote.data).decode(PaneOwnershipEvent.serializer()).owner)
        assertEquals(PaneOwner.Mac("MacBook"), checkNotNull(mac.data).decode(PaneOwnershipEvent.serializer()).owner)
    }

    @Test
    fun paneOwnersEncodeAsTheyDecode() {
        val owner = PaneOwner.Remote(UUID.randomUUID(), "Pixel")
        val event = PaneOwnershipEvent(UUID.randomUUID(), owner)
        assertEquals(
            event,
            ProtocolJson.decodeFromString(
                PaneOwnershipEvent.serializer(),
                ProtocolJson.encodeToString(PaneOwnershipEvent.serializer(), event),
            ),
        )
    }

    @Test
    fun terminalInputParamsEncodeBytesAsBase64() {
        val json =
            ProtocolJson
                .encodeToJsonElement(
                    TerminalInputParams.serializer(),
                    TerminalInputParams("pane-1", byteArrayOf(0x03)),
                ).jsonObject
        assertEquals("pane-1", json.getValue("paneID").jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(0x03)), json.getValue("bytes").jsonPrimitive.content)
    }

    @Test
    fun takeOverPaneParamsEncodeColumnsAndRows() {
        val json = ProtocolJson.encodeToJsonElement(TakeOverPaneParams.serializer(), TakeOverPaneParams("pane-1", 120, 40)).jsonObject
        assertEquals(120, json.getValue("cols").jsonPrimitive.int)
        assertEquals(40, json.getValue("rows").jsonPrimitive.int)
    }

    @Test
    fun setClientThemeParamsEncodeTheTheme() {
        val params = SetClientThemeParams(ThemeCatalog.muxy.clientTerminalTheme())
        val theme =
            ProtocolJson
                .encodeToJsonElement(SetClientThemeParams.serializer(), params)
                .jsonObject
                .getValue("theme")
                .jsonObject
        assertEquals(0xC9C2D9, theme.getValue("fg").jsonPrimitive.int)
        assertEquals(0x19171F, theme.getValue("bg").jsonPrimitive.int)
        assertEquals(16, theme.getValue("palette").jsonArray.size)
        assertEquals(0xC370D3, theme.getValue("cursorColor").jsonPrimitive.int)
        assertEquals(0x19171F, theme.getValue("selectionForeground").jsonPrimitive.int)
    }
}
