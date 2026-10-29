package com.muxy.app.features.tabs

import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.Tab
import com.muxy.app.models.TabArea
import com.muxy.app.models.TabKind
import com.muxy.app.models.Workspace
import com.muxy.app.models.WorkspaceNode
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.testing.Frames
import com.muxy.app.testing.TransportRecorder
import com.muxy.app.testing.connectionManager
import com.muxy.app.testing.device
import com.muxy.app.testing.tokenStoreWith
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DeviceTabsCatalogTest {
    private val studio = device()
    private val projectId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val areaId = UUID.randomUUID()
    private val zsh = Tab(UUID.randomUUID(), TabKind.Terminal, "zsh", false)
    private val nvim = Tab(UUID.randomUUID(), TabKind.Terminal, "nvim", false)
    private val workspace =
        Workspace(projectId, UUID.randomUUID(), areaId, WorkspaceNode.Area(TabArea(areaId, "/muxy", listOf(zsh, nvim), zsh.id)))
    private val closeRequests = mutableListOf<Map<String, String>>()
    private var failsClosing = false

    private val recorder =
        TransportRecorder { _, frame ->
            val id = Frames.id(frame)
            when (Frames.method(frame)) {
                "authenticateDevice" -> {
                    listOf(Frames.pairing(id))
                }

                "listProjects" -> {
                    listOf(
                        Frames.result(id, ResultType.PROJECTS, """{ "projects": [ ${Frames.project(projectId.uuidString, "muxy")} ] }"""),
                    )
                }

                "getWorkspace" -> {
                    listOf(Frames.result(id, ResultType.WORKSPACE, ProtocolJson.encodeToString(Workspace.serializer(), workspace)))
                }

                "closeTab" -> {
                    closeRequests += Frames.params(frame).mapValues { it.value.jsonPrimitive.content }
                    listOf(if (failsClosing) Frames.error(id, 500) else Frames.result(id, ResultType.OK, "{}"))
                }

                else -> {
                    emptyList()
                }
            }
        }

    @Test
    fun closingATabRemovesItImmediatelyAndSendsItsIds() =
        runTest {
            val catalog = catalog()
            assertEquals(listOf("zsh", "nvim"), titles(catalog))

            catalog.closeTab(projectId.uuidString, zsh.id)

            assertEquals(listOf("nvim"), titles(catalog))
            runCurrent()
            assertEquals(
                listOf(mapOf("projectID" to projectId.uuidString, "areaID" to areaId.uuidString, "tabID" to zsh.id.uuidString)),
                closeRequests,
            )
            assertEquals(listOf("nvim"), titles(catalog))
        }

    @Test
    fun aFailedCloseReloadsTheTabs() =
        runTest {
            failsClosing = true
            val catalog = catalog()

            catalog.closeTab(projectId.uuidString, nvim.id)

            assertEquals(listOf("zsh"), titles(catalog))
            runCurrent()
            assertEquals(1, closeRequests.size)
            assertEquals(listOf("zsh", "nvim"), titles(catalog))
        }

    @Test
    fun closingAnUnknownTabSendsNothing() =
        runTest {
            val catalog = catalog()

            catalog.closeTab(projectId.uuidString, UUID.randomUUID())
            catalog.closeTab(UUID.randomUUID().uuidString, zsh.id)
            runCurrent()

            assertTrue(closeRequests.isEmpty())
            assertEquals(listOf("zsh", "nvim"), titles(catalog))
        }

    private suspend fun TestScope.catalog(): DeviceTabsCatalog {
        val manager = connectionManager(recorder, tokenStoreWith(studio))
        return DeviceTabsCatalog(studio, manager, backgroundScope).apply {
            setActive(true)
            runCurrent()
        }
    }

    private fun titles(catalog: DeviceTabsCatalog): List<String> =
        catalog.state.value.projects
            .single()
            .tabs
            .map { it.title }
}
