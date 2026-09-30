package com.muxy.app.features.projectdetail

import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.models.Project
import com.muxy.app.networking.muxy1.ConnectionManager
import com.muxy.app.networking.muxy1.protocol.Method
import com.muxy.app.networking.muxy1.protocol.ProjectsResult
import com.muxy.app.persistence.secrets.SecretTokenStore
import com.muxy.app.testing.InMemoryConnectionStore
import com.muxy.app.testing.InMemorySecretStore
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.TransportRecorder
import com.muxy.app.testing.connectionManager
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProjectDetailViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun loadsTheWorkspaceAndSelectsTheActiveTab() =
        runTest {
            val fixture = fixture()
            val state = fixture.viewModel.uiState.value
            assertEquals(ProjectTabsStatus.READY, state.status)
            assertEquals(listOf("zsh"), state.tabs.map { it.title })
            assertEquals(state.tabs.single().id, state.selectedTabId)
            assertEquals("Muxy", state.projectName)
            assertEquals(DemoConnection.NAME, state.connectionName)
        }

    @Test
    fun showsLoadingUntilConnected() =
        runTest {
            val fixture = fixture(connect = false)
            assertEquals(ProjectTabsStatus.LOADING, fixture.viewModel.uiState.value.status)
        }

    @Test
    fun creatingATabSelectsIt() =
        runTest {
            val fixture = fixture()
            fixture.viewModel.createTab()
            advanceUntilIdle()
            val state = fixture.viewModel.uiState.value
            assertEquals(listOf("zsh", "zsh 3"), state.tabs.map { it.title })
            assertEquals(state.tabs.last().id, state.selectedTabId)
        }

    @Test
    fun selectingATabIsSentToTheMac() =
        runTest {
            val fixture = fixture()
            fixture.viewModel.createTab()
            advanceUntilIdle()
            val first =
                fixture.viewModel.uiState.value.tabs
                    .first()
            fixture.viewModel.select(first)
            advanceUntilIdle()
            assertEquals(first.id, fixture.viewModel.uiState.value.selectedTabId)
            val reopened = fixture.newViewModel()
            advanceUntilIdle()
            assertEquals(first.id, reopened.uiState.value.selectedTabId)
        }

    @Test
    fun closingATabRemovesItAndSelectsAnother() =
        runTest {
            val fixture = fixture()
            fixture.viewModel.createTab()
            advanceUntilIdle()
            val created =
                fixture.viewModel.uiState.value.tabs
                    .last()
            fixture.viewModel.closeTab(created)
            val optimistic = fixture.viewModel.uiState.value
            assertFalse(optimistic.tabs.any { it.id == created.id })
            assertEquals(optimistic.tabs.single().id, optimistic.selectedTabId)
            advanceUntilIdle()
            assertEquals(
                listOf("zsh"),
                fixture.viewModel.uiState.value.tabs
                    .map { it.title },
            )
        }

    @Test
    fun closingTheLastTabShowsTheEmptyState() =
        runTest {
            val fixture = fixture()
            fixture.viewModel.closeTab(
                fixture.viewModel.uiState.value.tabs
                    .single(),
            )
            advanceUntilIdle()
            val state = fixture.viewModel.uiState.value
            assertTrue(state.tabs.isEmpty())
            assertEquals(ProjectTabsStatus.READY, state.status)
        }

    private suspend fun TestScope.fixture(connect: Boolean = true): Fixture {
        val tokens = SecretTokenStore(InMemorySecretStore()).apply { setCredential(DemoConnection.credential, DemoConnection.id) }
        val manager = connectionManager(TransportRecorder(), tokens)
        manager.ensureConnected(DemoConnection.connection)
        val project =
            manager
                .request(DemoConnection.id, Method.LIST_PROJECTS)
                .decode(ProjectsResult.serializer())
                .projects
                .first()
        if (!connect) manager.disconnect()
        return Fixture(manager, project).also { advanceUntilIdle() }
    }

    private class Fixture(
        private val manager: ConnectionManager,
        private val project: Project,
    ) {
        private val store = InMemoryConnectionStore(listOf(DemoConnection.connection))
        val viewModel = newViewModel()

        fun newViewModel() = ProjectDetailViewModel(DemoConnection.id, project.id, project.name, store, manager)
    }
}
