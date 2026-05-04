package com.muxy.app.demo

import com.muxy.app.model.ProjectIDParams
import com.muxy.app.model.MuxyJson
import com.muxy.app.model.SplitNodeDTO
import com.muxy.app.model.TaggedValue
import com.muxy.app.model.WorkspaceDTO
import com.muxy.app.model.VCSBranchesDTO
import com.muxy.app.model.MuxyRequest
import com.muxy.app.model.ProjectDTO
import com.muxy.app.model.VCSCreateBranchParams
import com.muxy.app.model.collectAreas
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBackendTest {

    private val backend = DemoBackend()
    private val noEmit: suspend (com.muxy.app.model.MuxyEvent) -> Unit = { _ -> }

    @Test
    fun listProjectsReturnsSeededProjects() = runTest {
        val resp = backend.handle(MuxyRequest(id = "1", method = "listProjects"), noEmit)
        assertEquals("projects", resp.result?.type)
        val list = MuxyJson.decodeFromJsonElement(
            ListSerializer(ProjectDTO.serializer()),
            resp.result!!.value!!,
        )
        assertEquals(listOf("muxy", "web-app"), list.map { it.name })
    }

    @Test
    fun getWorkspaceReturnsTwoTabsForMuxy() = runTest {
        val params = TaggedValue(
            type = "getWorkspace",
            value = MuxyJson.encodeToJsonElement(
                ProjectIDParams.serializer(),
                ProjectIDParams("11111111-1111-1111-1111-111111111111"),
            ),
        )
        val resp = backend.handle(MuxyRequest(id = "1", method = "getWorkspace", params = params), noEmit)
        val ws = MuxyJson.decodeFromJsonElement(WorkspaceDTO.serializer(), resp.result!!.value!!)
        val areas = ws.root.collectAreas()
        assertEquals(1, areas.size)
        assertEquals(2, areas.first().tabs.size)
    }

    @Test
    fun createBranchAppendsAndSwitches() = runTest {
        val params = TaggedValue(
            type = "vcsCreateBranch",
            value = MuxyJson.encodeToJsonElement(
                VCSCreateBranchParams.serializer(),
                VCSCreateBranchParams("11111111-1111-1111-1111-111111111111", "feature/demo"),
            ),
        )
        val resp = backend.handle(MuxyRequest(id = "1", method = "vcsCreateBranch", params = params), noEmit)
        assertEquals("ok", resp.result?.type)
        val listParams = TaggedValue(
            type = "vcsListBranches",
            value = MuxyJson.encodeToJsonElement(
                ProjectIDParams.serializer(),
                ProjectIDParams("11111111-1111-1111-1111-111111111111"),
            ),
        )
        val listResp = backend.handle(MuxyRequest(id = "2", method = "vcsListBranches", params = listParams), noEmit)
        val branches = MuxyJson.decodeFromJsonElement(VCSBranchesDTO.serializer(), listResp.result!!.value!!)
        assertEquals("feature/demo", branches.current)
        assertTrue(branches.locals.contains("feature/demo"))
    }

    @Test
    fun unknownProjectGetWorkspaceReturnsNotFound() = runTest {
        val params = TaggedValue(
            type = "getWorkspace",
            value = MuxyJson.encodeToJsonElement(
                ProjectIDParams.serializer(),
                ProjectIDParams("99999999-9999-9999-9999-999999999999"),
            ),
        )
        val resp = backend.handle(MuxyRequest(id = "1", method = "getWorkspace", params = params), noEmit)
        assertNull(resp.result)
        assertNotNull(resp.error)
    }
}
