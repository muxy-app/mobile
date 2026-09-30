package com.muxy.app.models

import com.muxy.app.networking.muxy1.protocol.ProjectsResult
import com.muxy.app.networking.muxy1.protocol.ProtocolJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectDecodingTest {
    @Test
    fun decodesProjectWithAllFields() {
        val project =
            decode(
                """
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "name": "muxy",
                  "path": "/Users/example/project",
                  "sortOrder": 3,
                  "createdAt": "2026-04-19T10:00:00Z",
                  "icon": "hammer",
                  "logo": "a1b2c3d4",
                  "iconColor": "#7C3AED",
                  "preferredWorktreeParentPath": "/Users/example",
                  "workspaceID": "22222222-2222-2222-2222-222222222222",
                  "workspaceName": "Work",
                  "unknownField": true
                }
                """,
            )
        assertEquals("muxy", project.name)
        assertEquals(3.0, project.sortOrder, 0.0)
        assertEquals("hammer", project.icon)
        assertEquals("#7C3AED", project.iconColor)
        assertEquals("/Users/example", project.preferredWorktreeParentPath)
        assertEquals("Work", project.workspaceName)
    }

    @Test
    fun decodesProjectWithOptionalsOmitted() {
        val project =
            decode(
                """
                { "id": "11111111-1111-1111-1111-111111111111", "name": "muxy", "path": "/Users/example/project",
                  "sortOrder": 0, "createdAt": "2026-04-19T10:00:00Z" }
                """,
            )
        assertNull(project.icon)
        assertNull(project.logo)
        assertNull(project.iconColor)
        assertNull(project.preferredWorktreeParentPath)
        assertNull(project.workspaceId)
    }

    @Test
    fun projectsResultDecodesWrappedObject() {
        val result =
            ProtocolJson.decodeFromString(
                ProjectsResult.serializer(),
                """{ "projects": [ { "id": "11111111-1111-1111-1111-111111111111", "name": "a", "path": "/a", "sortOrder": 0, "createdAt": "2026-04-19T10:00:00Z" } ] }""",
            )
        assertEquals(listOf("a"), result.projects.map(Project::name))
    }

    @Test
    fun projectsResultDecodesBareArray() {
        val result =
            ProtocolJson.decodeFromString(
                ProjectsResult.serializer(),
                """
                [
                  { "id": "11111111-1111-1111-1111-111111111111", "name": "a", "path": "/a", "sortOrder": 0, "createdAt": "2026-04-19T10:00:00Z" },
                  { "id": "22222222-2222-2222-2222-222222222222", "name": "b", "path": "/b", "sortOrder": 1, "createdAt": "2026-04-19T10:00:00Z" }
                ]
                """,
            )
        assertEquals(listOf("a", "b"), result.projects.map(Project::name))
    }

    @Test
    fun decodesFloatingPointSortOrder() {
        val project =
            decode(
                """
                { "id": "00000000-0000-0000-0000-000000000001", "name": "Home", "path": "/Users/saeed",
                  "sortOrder": -9.223372036854776e+18, "createdAt": "2026-06-07T20:48:51Z" }
                """,
            )
        assertEquals("Home", project.name)
        assertTrue(project.sortOrder < 0)
    }

    @Test
    fun projectsResultDecodesArrayWithFloatingPointSortOrder() {
        val result =
            ProtocolJson.decodeFromString(
                ProjectsResult.serializer(),
                """
                [
                  { "id": "00000000-0000-0000-0000-000000000001", "name": "Home", "path": "/Users/saeed", "sortOrder": -9.223372036854776e+18, "createdAt": "2026-06-07T20:48:51Z" },
                  { "id": "22222222-2222-2222-2222-222222222222", "name": "muxy", "path": "/p", "sortOrder": 0, "createdAt": "2026-04-19T10:00:00Z" }
                ]
                """,
            )
        assertEquals("Home", result.projects.minBy(Project::sortOrder).name)
    }

    @Test
    fun encodesProjectsResultAsAWrappedObject() {
        val encoded = ProtocolJson.encodeToString(ProjectsResult.serializer(), ProjectsResult(emptyList()))
        assertEquals("""{"projects":[]}""", encoded)
    }

    private fun decode(json: String): Project = ProtocolJson.decodeFromString(Project.serializer(), json)
}
