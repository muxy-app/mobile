package com.muxy.app.features.demo

import com.muxy.app.core.serialization.parseUuid
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.models.Project
import com.muxy.app.models.TabKind
import com.muxy.app.models.Workspace
import com.muxy.app.models.WorkspaceFlattening
import com.muxy.app.networking.muxy1.protocol.ErrorCode
import com.muxy.app.networking.muxy1.protocol.GetWorkspaceParams
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.PairingResult
import com.muxy.app.networking.muxy1.protocol.ProjectsResult
import com.muxy.app.networking.muxy1.protocol.ProtocolException
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.persistence.secrets.SecretTokenStore
import com.muxy.app.testing.InMemoryConnectionStore
import com.muxy.app.testing.InMemorySecretStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBackendTest {
    private val backend = DemoBackend()

    @Test
    fun authenticateReturnsAPairing() {
        val result = backend.authenticate()
        val pairing = result.decode(PairingResult.serializer())
        assertEquals(ResultType.PAIRING, result.type)
        assertEquals("Demo Desktop", pairing.deviceName)
        assertEquals(backend.clientId, parseUuid(pairing.clientId))
    }

    @Test
    fun listProjectsReturnsTheSampleProjects() =
        runTest {
            val result = backend.handle(Method.LIST_PROJECTS, null).result
            assertEquals(ResultType.PROJECTS, result.type)
            val projects = result.decode(ProjectsResult.serializer()).projects
            assertEquals(listOf("Muxy", "Web App"), projects.map(Project::name))
            assertEquals(listOf("Work", "Personal"), projects.map { it.workspaceName })
        }

    @Test
    fun theWorkspaceLoadsForASampleProject() =
        runTest {
            val project = firstProject()
            val result = backend.handle(Method.GET_WORKSPACE, params(GetWorkspaceParams(project.id.uuidString))).result
            val workspace = result.decode(Workspace.serializer())
            assertEquals(ResultType.WORKSPACE, result.type)
            assertEquals(project.id, workspace.projectId)
            assertEquals(
                TabKind.Terminal,
                WorkspaceFlattening
                    .focusedTabArea(workspace)
                    ?.tabs
                    ?.first()
                    ?.kind,
            )
        }

    @Test
    fun unavailableMethodsAreNotFound() =
        runTest {
            val error = runCatching { backend.handle(Method.GET_PROJECT_LOGO, null) }.exceptionOrNull() as ProtocolException
            assertEquals(ErrorCode.NOT_FOUND, error.code)
            assertEquals("Not available in demo mode", error.body.message)
        }

    @Test
    fun badParamsAreRejected() =
        runTest {
            val error =
                runCatching {
                    backend.handle(Method.GET_WORKSPACE, buildJsonObject { put("projectID", JsonPrimitive("nope")) })
                }.exceptionOrNull()
            assertEquals(ErrorCode.INVALID_PARAMS, (error as ProtocolException).code)
        }

    @Test
    fun applyingDemoModeAddsAndRemovesTheConnectionAndToken() =
        runTest {
            val store = InMemoryConnectionStore()
            val tokens = SecretTokenStore(InMemorySecretStore())
            DemoConnection.apply(enabled = true, store = store, tokens = tokens)
            assertEquals(listOf(DemoConnection.connection), store.load())
            assertEquals(DemoConnection.TOKEN, tokens.credential(DemoConnection.id)?.token)
            DemoConnection.apply(enabled = false, store = store, tokens = tokens)
            assertTrue(store.load().isEmpty())
            assertNull(tokens.credential(DemoConnection.id))
        }

    private suspend fun firstProject(): Project =
        backend
            .handle(Method.LIST_PROJECTS, null)
            .result
            .decode(ProjectsResult.serializer())
            .projects
            .first()

    private fun params(value: GetWorkspaceParams) = ProtocolJson.encodeToJsonElement(GetWorkspaceParams.serializer(), value)
}
