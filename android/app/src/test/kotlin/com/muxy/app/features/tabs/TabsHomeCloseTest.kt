package com.muxy.app.features.tabs

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.muxy.app.features.billing.BillingEnforcement
import com.muxy.app.features.billing.BillingRepository
import com.muxy.app.features.billing.FakePlayBilling
import com.muxy.app.features.billing.FakeTrialStore
import com.muxy.app.features.server.ServerDirectory
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind
import com.muxy.app.testing.FakeServerConnection
import com.muxy.app.testing.FakeServerConnector
import com.muxy.app.testing.InMemoryConnectionStore
import com.muxy.app.testing.InMemoryCredentialStore
import com.muxy.app.testing.serverCredential
import com.muxy.app.testing.session
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class TabsHomeCloseTest {
    private val owner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle
                get() = error("Not used")
        }

    private val studio =
        Connection(UUID.randomUUID(), "Studio", "studio.local", 7419, kind = ConnectionKind.SERVER, serverId = "server-1")

    private val key = TabsProjectKey(studio.id, "muxy")

    @Test
    fun closingAServerTabEndsItsSessionAndRemovesItFromHome() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(1u), session(2u)))
            val viewModel = home(connection)
            val (first, second) = tabs(viewModel)

            assertNull(viewModel.closeTab(key, first.id))
            runCurrent()
            assertEquals(listOf(1uL), connection.ended)
            assertEquals(listOf(second.id), tabs(viewModel).map { it.id })
        }

    @Test
    fun aServerTabThatFailsToCloseComesBack() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(1u), session(2u))).apply { failsEnding = true }
            val viewModel = home(connection)

            viewModel.closeTab(key, tabs(viewModel).first().id)
            runCurrent()

            assertEquals(emptyList<ULong>(), connection.ended)
            assertEquals(2, tabs(viewModel).size)
        }

    @Test
    fun closingAnUnknownServerTabLeavesTheSessionsAlone() =
        runTest {
            val connection = FakeServerConnection(sessions = listOf(session(1u)))
            val viewModel = home(connection)

            viewModel.closeTab(key, UUID.randomUUID())
            runCurrent()

            assertEquals(emptyList<ULong>(), connection.ended)
            assertEquals(1, tabs(viewModel).size)
        }

    private suspend fun TestScope.home(connection: FakeServerConnection): TabsHomeViewModel {
        val credentials = InMemoryCredentialStore().apply { items["server-1"] = serverCredential() }
        val directory = ServerDirectory(credentials, FakeServerConnector(listOf(Result.success(connection))), backgroundScope)
        val billing = BillingRepository(FakePlayBilling(), FakeTrialStore(), BillingEnforcement(isDebug = true), backgroundScope)
        billing.refresh()
        val catalog =
            TabsCatalog(InMemoryConnectionStore(listOf(studio)), billing, directory, { error("No Muxy 1 devices") }, backgroundScope)
        directory.onStart(owner)
        catalog.onStart(owner)
        catalog.setVisible(true)
        runCurrent()
        return TabsHomeViewModel(catalog)
    }

    private fun tabs(viewModel: TabsHomeViewModel): List<TabsTab> =
        viewModel.devices.value
            .orEmpty()
            .single()
            .projects
            .single { it.id == "muxy" }
            .tabs
}
