package com.muxy.app.features.projects

import com.muxy.app.models.Connection
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.networking.muxy1.protocol.EventName
import com.muxy.app.networking.muxy1.protocol.ResultType
import com.muxy.app.persistence.secrets.TokenStore
import com.muxy.app.testing.Frames
import com.muxy.app.testing.InMemoryConnectionStore
import com.muxy.app.testing.InMemoryWorkspaceSelectionStore
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.TransportRecorder
import com.muxy.app.testing.connectionManager
import com.muxy.app.testing.credential
import com.muxy.app.testing.device
import com.muxy.app.testing.tokenStoreWith
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Base64
import java.util.UUID

class ProjectsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val work = UUID.randomUUID()
    private val personal = UUID.randomUUID()
    private val alpha = "11111111-1111-1111-1111-111111111111"
    private val beta = "22222222-2222-2222-2222-222222222222"
    private val png = byteArrayOf(1, 2, 3)
    private var listProjectsReply: (String) -> String = { id -> projects(id) }

    @Test
    fun loadsProjectsSortedAfterConnecting() =
        runTest {
            val fixture = fixture()
            fixture.connect()
            assertEquals(
                listOf("alpha", "beta"),
                fixture.viewModel.uiState.value.items
                    .map { it.name },
            )
            assertEquals("Studio", fixture.viewModel.uiState.value.connectionName)
        }

    @Test
    fun showsLoadingBeforeTheFirstAttempt() =
        runTest {
            assertEquals(
                ProjectListStatus.Loading,
                fixture()
                    .viewModel.uiState.value.status,
            )
        }

    @Test
    fun aMissingTokenAsksToPairAgain() =
        runTest {
            val fixture = fixture(withCredential = false)
            fixture.manager.ensureConnected(fixture.studio)
            advanceUntilIdle()
            assertEquals(ProjectListStatus.NeedsPairing, fixture.viewModel.uiState.value.status)
        }

    @Test
    fun anotherMacsConnectionReadsAsLoading() =
        runTest {
            val fixture = fixture()
            val laptop = device(name = "Laptop", host = "laptop.local")
            val manager = fixture.manager
            fixture.tokens.setCredential(credential(laptop), laptop.id)
            manager.ensureConnected(laptop)
            advanceUntilIdle()
            assertEquals(ProjectListStatus.Loading, fixture.viewModel.uiState.value.status)
            assertTrue(
                fixture.viewModel.uiState.value.items
                    .isEmpty(),
            )
        }

    @Test
    fun filtersByWorkspaceAndRemembersTheChoice() =
        runTest {
            val fixture = fixture()
            fixture.connect()
            assertEquals(
                listOf("Work", "Personal"),
                fixture.viewModel.uiState.value.workspaces
                    .map { it.name },
            )
            fixture.viewModel.selectWorkspace(personal)
            advanceUntilIdle()
            assertEquals(
                listOf("beta"),
                fixture.viewModel.uiState.value.items
                    .map { it.name },
            )
            assertEquals(personal, fixture.selections.selections[fixture.studio.id])
            val reopened = fixture.newViewModel()
            advanceUntilIdle()
            assertEquals(personal, reopened.uiState.value.selectedWorkspaceId)
        }

    @Test
    fun dropsASelectionWhoseWorkspaceIsGone() =
        runTest {
            val fixture = fixture()
            fixture.selections.selections[fixture.studio.id] = UUID.randomUUID()
            val viewModel = fixture.newViewModel()
            fixture.connect()
            assertNull(viewModel.uiState.value.selectedWorkspaceId)
            assertNull(fixture.selections.selections[fixture.studio.id])
        }

    @Test
    fun projectsChangedReplacesTheList() =
        runTest {
            val fixture = fixture()
            fixture.connect()
            fixture.recorder.latest!!.enqueue(
                Frames.event(EventName.PROJECTS_CHANGED, "projects", """{ "projects": [ ${Frames.project(alpha, "renamed")} ] }"""),
            )
            advanceUntilIdle()
            assertEquals(
                listOf("renamed"),
                fixture.viewModel.uiState.value.items
                    .map { it.name },
            )
        }

    @Test
    fun loadsLogosAndIgnoresMissingOnes() =
        runTest {
            val fixture = fixture()
            fixture.connect()
            val items = fixture.viewModel.uiState.value.items
            assertArrayEquals(png, items.first { it.name == "alpha" }.logo?.png)
            assertNull(items.first { it.name == "beta" }.logo)
        }

    @Test
    fun aFailedLoadOffersRetry() =
        runTest {
            listProjectsReply = { id -> Frames.error(id, 500) }
            val fixture = fixture()
            fixture.connect()
            assertEquals(ProjectListStatus.LoadFailed, fixture.viewModel.uiState.value.status)
            listProjectsReply = { id -> projects(id) }
            fixture.viewModel.retry()
            advanceUntilIdle()
            assertEquals(
                listOf("alpha", "beta"),
                fixture.viewModel.uiState.value.items
                    .map { it.name },
            )
        }

    private fun projects(id: String): String =
        Frames.result(
            id,
            ResultType.PROJECTS,
            """{ "projects": [
              ${Frames.project(beta, "beta", 1.0, personal.toString(), "Personal", logo = "missing")},
              ${Frames.project(alpha, "alpha", 0.0, work.toString(), "Work", logo = "logo")}
            ] }""",
        )

    private fun reply(frame: String): List<String> {
        val id = Frames.id(frame)
        return when (Frames.method(frame)) {
            "authenticateDevice" -> {
                listOf(Frames.pairing(id))
            }

            "listProjects" -> {
                listOf(listProjectsReply(id))
            }

            "getProjectLogo" -> {
                val projectId =
                    Frames
                        .params(frame)
                        .getValue("projectID")
                        .toString()
                        .trim('"')
                if (projectId == alpha) {
                    listOf(
                        Frames.result(
                            id,
                            ResultType.PROJECT_LOGO,
                            """{ "projectID": "$alpha", "pngData": "${Base64.getEncoder().encodeToString(png)}" }""",
                        ),
                    )
                } else {
                    listOf(Frames.error(id, 404))
                }
            }

            else -> {
                emptyList()
            }
        }
    }

    private suspend fun TestScope.fixture(withCredential: Boolean = true): Fixture {
        val studio = device()
        return Fixture(this, studio, if (withCredential) tokenStoreWith(studio) else tokenStoreWith())
    }

    private inner class Fixture(
        private val test: TestScope,
        val studio: Connection,
        val tokens: TokenStore,
    ) {
        val recorder = TransportRecorder { _, frame -> reply(frame) }
        val selections = InMemoryWorkspaceSelectionStore()
        private val store = InMemoryConnectionStore(listOf(studio))
        val manager: ConnectionManager = test.connectionManager(recorder, tokens)
        val viewModel = newViewModel()

        fun newViewModel() = ProjectsViewModel(studio.id, store, manager, selections)

        suspend fun connect() {
            manager.ensureConnected(studio)
            test.advanceUntilIdle()
        }
    }
}
