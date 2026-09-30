package com.muxy.app.features.addconnection

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.muxy.app.core.Endpoint
import com.muxy.app.core.security.TokenGenerator
import com.muxy.app.core.serialization.uuidString
import com.muxy.app.core.validation.ConnectionInputValidator
import com.muxy.app.models.ConnectionKind
import com.muxy.app.models.DiscoverySource
import com.muxy.app.models.PairingState
import com.muxy.app.networking.muxy1.PairingUri
import com.muxy.app.networking.muxy1.discovery.DiscoveredService
import com.muxy.app.networking.muxy1.transport.TransportException
import com.muxy.app.networking.muxy1.transport.TransportFailure
import com.muxy.app.persistence.secrets.SecretTokenStore
import com.muxy.app.testing.FakeServiceDiscovery
import com.muxy.app.testing.Frames
import com.muxy.app.testing.InMemoryConnectionStore
import com.muxy.app.testing.InMemorySecretStore
import com.muxy.app.testing.MainDispatcherRule
import com.muxy.app.testing.PHONE_NAME
import com.muxy.app.testing.TransportRecorder
import com.muxy.app.testing.connectionManager
import com.muxy.app.testing.credential
import com.muxy.app.testing.device
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddConnectionViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun defaultsThePort() =
        runTest {
            assertEquals(Endpoint.DEFAULT_PORT.toString(), Fixture(this).viewModel.portText)
        }

    @Test
    fun cannotSubmitWhenEmpty() =
        runTest {
            assertFalse(Fixture(this).viewModel.canSubmit)
        }

    @Test
    fun canSubmitValidInput() =
        runTest {
            val viewModel = Fixture(this).viewModel
            viewModel.name = "Studio"
            viewModel.host = "studio.local"
            assertTrue(viewModel.canSubmit)
        }

    @Test
    fun applyScanFillsTheFormAndSource() =
        runTest {
            val viewModel = Fixture(this).viewModel
            viewModel.applyScan(PairingUri("studio.local", 5000, "Studio", "My Mac"))
            assertEquals("studio.local", viewModel.host)
            assertEquals("5000", viewModel.portText)
            assertEquals("My Mac", viewModel.name)
            assertEquals(DiscoverySource.QR, viewModel.discoverySource)
        }

    @Test
    fun applyDiscoveredFillsTheFormAndSource() =
        runTest {
            val viewModel = Fixture(this).viewModel
            viewModel.applyDiscovered(DiscoveredService("Studio", "studio.local", 4865))
            assertEquals("Studio", viewModel.name)
            assertEquals("studio.local", viewModel.host)
            assertEquals("4865", viewModel.portText)
            assertEquals(DiscoverySource.BONJOUR, viewModel.discoverySource)
        }

    @Test
    fun exposesDiscoveredServices() =
        runTest {
            val service = DiscoveredService("Studio", "studio.local", 4865)
            assertEquals(listOf(service), Fixture(this, services = listOf(service)).viewModel.discoveredServices.value)
        }

    @Test
    fun discoveryRunsWhileTheScreenIsOpen() =
        runTest {
            val fixture = Fixture(this)
            val store = ViewModelStore()
            ViewModelProvider.create(store, viewModelFactory { initializer { fixture.viewModel } })[AddConnectionViewModel::class]
            assertTrue(fixture.discovery.isBrowsing)
            store.clear()
            assertFalse(fixture.discovery.isBrowsing)
        }

    @Test
    fun anInvalidCodeShowsTheAlert() =
        runTest {
            val viewModel = Fixture(this).viewModel
            assertFalse(viewModel.applyPairingCode("https://example.com"))
            assertEquals(AddConnectionAlert.INVALID_CODE, viewModel.alert)
            viewModel.dismissAlert()
            assertNull(viewModel.alert)
        }

    @Test
    fun aDeliveredCodeFillsTheForm() =
        runTest {
            val fixture = Fixture(this)
            fixture.inbox.deliver(AddConnectionRequest.PairingCode("muxy://pair?host=10.0.2.2&port=4865&label=Studio"))
            assertEquals("10.0.2.2", fixture.viewModel.host)
            assertEquals("Studio", fixture.viewModel.name)
            assertNull(fixture.inbox.request.value)
        }

    @Test
    fun anUnavailableScannerShowsTheAlert() =
        runTest {
            val viewModel = Fixture(this).viewModel
            viewModel.onScanResult(QrScanResult.Unavailable)
            assertEquals(AddConnectionAlert.SCANNER_UNAVAILABLE, viewModel.alert)
        }

    @Test
    fun pairingANewMacSavesItAsPaired() =
        runTest {
            val fixture = Fixture(this)
            fixture.fill()
            fixture.viewModel.submit()
            advanceUntilIdle()
            val added = fixture.viewModel.addedConnection!!
            assertEquals(listOf(added), fixture.store.load())
            assertEquals(PairingState.PAIRED, added.pairingState)
            assertEquals(ConnectionKind.DEVICE, added.kind)
            assertEquals(added.id.uuidString, fixture.tokens.credential(added.id)?.deviceId)
            assertEquals(AddConnectionStatus.Succeeded, fixture.viewModel.status)
            assertEquals(PHONE_NAME, fixture.sentAuth("deviceName"))
        }

    @Test
    fun addingASavedMacAtTheSameAddressReusesItsCredential() =
        runTest {
            val saved = device(name = "Old Name", host = "192.168.1.20", serviceName = "Studio")
            val fixture = Fixture(this)
            fixture.store.upsert(saved)
            fixture.tokens.setCredential(credential(saved), saved.id)
            fixture.viewModel.applyDiscovered(DiscoveredService("Studio", "192.168.1.20", 4865))
            fixture.viewModel.submit()
            advanceUntilIdle()
            val connections = fixture.store.load()
            assertEquals(listOf(saved.id), connections.map { it.id })
            assertEquals("Studio", connections.single().name)
            assertEquals(credential(saved).deviceId, fixture.sentAuth("deviceID"))
            assertEquals(credential(saved).token, fixture.sentAuth("token"))
            assertEquals(credential(saved), fixture.tokens.credential(saved.id))
        }

    @Test
    fun aSavedMacAtANewAddressNeverReceivesItsSavedToken() =
        runTest {
            val saved = device(host = "192.168.1.20", serviceName = "Studio")
            val fixture = Fixture(this)
            fixture.store.upsert(saved)
            fixture.tokens.setCredential(credential(saved), saved.id)
            fixture.inbox.deliver(AddConnectionRequest.PairingCode("muxy://pair?host=evil.example&service=Studio&label=Studio"))
            fixture.viewModel.submit()
            advanceUntilIdle()
            assertNotEquals(credential(saved).token, fixture.sentAuth("token"))
            assertNotEquals(credential(saved).deviceId, fixture.sentAuth("deviceID"))
            val updated = fixture.store.load().single()
            assertEquals(saved.id, updated.id)
            assertEquals("evil.example", updated.host)
            assertEquals(fixture.sentAuth("token"), fixture.tokens.credential(saved.id)?.token)
        }

    @Test
    fun aFailedPairingAtANewAddressKeepsTheSavedMacAndCredential() =
        runTest {
            val saved = device(host = "192.168.1.20", serviceName = "Studio")
            val fixture =
                Fixture(
                    this,
                    recorder =
                        TransportRecorder { _, frame ->
                            listOf(
                                Frames.error(
                                    Frames.id(frame),
                                    if (Frames.method(frame) ==
                                        "authenticateDevice"
                                    ) {
                                        401
                                    } else {
                                        403
                                    },
                                ),
                            )
                        },
                )
            fixture.store.upsert(saved)
            fixture.tokens.setCredential(credential(saved), saved.id)
            fixture.viewModel.applyDiscovered(DiscoveredService("Studio", "192.168.1.30", 4865))
            fixture.viewModel.submit()
            advanceUntilIdle()
            assertEquals(listOf(saved), fixture.store.load())
            assertEquals(credential(saved), fixture.tokens.credential(saved.id))
        }

    @Test
    fun editingTheAddressForgetsTheDiscoveredMac() =
        runTest {
            val studio = device(host = "192.168.1.20", serviceName = "Studio")
            val fixture = Fixture(this)
            fixture.store.upsert(studio)
            fixture.tokens.setCredential(credential(studio), studio.id)
            fixture.viewModel.applyDiscovered(DiscoveredService("Studio", "192.168.1.20", 4865))
            fixture.viewModel.host = "laptop.local"
            fixture.viewModel.name = "Laptop"
            assertEquals(DiscoverySource.MANUAL, fixture.viewModel.discoverySource)
            fixture.viewModel.submit()
            advanceUntilIdle()
            assertEquals(listOf("Studio", "Laptop"), fixture.store.load().map { it.name })
            assertEquals(credential(studio), fixture.tokens.credential(studio.id))
            assertNull(
                fixture.store
                    .load()
                    .last()
                    .serviceName,
            )
        }

    @Test
    fun aRepairRequestFillsTheFormWithTheSavedMac() =
        runTest {
            val studio = device(host = "192.168.1.20", serviceName = "Studio")
            val fixture = Fixture(this)
            fixture.store.upsert(studio)
            fixture.inbox.deliver(AddConnectionRequest.Repair(studio.id))
            advanceUntilIdle()
            assertEquals("Studio", fixture.viewModel.name)
            assertEquals("192.168.1.20", fixture.viewModel.host)
            assertEquals("4865", fixture.viewModel.portText)
            fixture.viewModel.submit()
            advanceUntilIdle()
            assertEquals(listOf(studio.id), fixture.store.load().map { it.id })
            assertEquals(fixture.sentAuth("token"), fixture.tokens.credential(studio.id)?.token)
        }

    @Test
    fun aSavedMacWithoutACredentialPairsAsANewDevice() =
        runTest {
            val saved = device(host = "studio.local")
            val fixture = Fixture(this)
            fixture.store.upsert(saved)
            fixture.fill(host = "STUDIO.local")
            fixture.viewModel.submit()
            advanceUntilIdle()
            assertEquals(listOf(saved.id), fixture.store.load().map { it.id })
            assertNotEquals(saved.id.uuidString, fixture.sentAuth("deviceID"))
            assertEquals(fixture.sentAuth("deviceID"), fixture.tokens.credential(saved.id)?.deviceId)
        }

    @Test
    fun aDeniedPairingSavesNoToken() =
        runTest {
            val fixture =
                Fixture(
                    this,
                    recorder =
                        TransportRecorder { _, frame ->
                            listOf(
                                Frames.error(
                                    Frames.id(frame),
                                    if (Frames.method(frame) ==
                                        "authenticateDevice"
                                    ) {
                                        401
                                    } else {
                                        403
                                    },
                                ),
                            )
                        },
                )
            fixture.fill()
            fixture.viewModel.submit()
            advanceUntilIdle()
            assertEquals(AddConnectionStatus.Failed("The Mac denied this device."), fixture.viewModel.status)
            assertTrue(fixture.secrets.values.isEmpty())
            assertTrue(fixture.store.load().isEmpty())
            assertNull(fixture.viewModel.addedConnection)
        }

    @Test
    fun aFailedPairingKeepsASavedCredential() =
        runTest {
            val saved = device(host = "studio.local")
            val fixture = Fixture(this, recorder = TransportRecorder { _, frame -> listOf(Frames.error(Frames.id(frame), 403)) })
            fixture.store.upsert(saved)
            fixture.tokens.setCredential(credential(saved), saved.id)
            fixture.fill()
            fixture.viewModel.submit()
            advanceUntilIdle()
            assertEquals(
                AddConnectionStatus.Failed("This device's credentials are invalid. Remove it and add it again."),
                fixture.viewModel.status,
            )
            assertEquals(credential(saved), fixture.tokens.credential(saved.id))
        }

    @Test
    fun anUnreachableMacShowsTheConnectMessage() =
        runTest {
            val fixture = Fixture(this, recorder = TransportRecorder(connectFailure = TransportException(TransportFailure.TIMED_OUT)))
            fixture.fill()
            fixture.viewModel.submit()
            advanceUntilIdle()
            assertEquals(AddConnectionStatus.Failed("Couldn't connect. Check the host and port."), fixture.viewModel.status)
            assertTrue(fixture.secrets.values.isEmpty())
        }

    private class Fixture(
        test: TestScope,
        services: List<DiscoveredService> = emptyList(),
        private val recorder: TransportRecorder = TransportRecorder(),
    ) {
        val store = InMemoryConnectionStore()
        val secrets = InMemorySecretStore()
        val tokens = SecretTokenStore(secrets)
        val discovery = FakeServiceDiscovery(services)
        val inbox = AddConnectionInbox()
        val viewModel =
            AddConnectionViewModel(
                store = store,
                tokens = tokens,
                manager = test.connectionManager(recorder, tokens),
                validator = ConnectionInputValidator(),
                tokenGenerator = TokenGenerator(),
                discovery = discovery,
                inbox = inbox,
            )

        fun fill(host: String = "studio.local") {
            viewModel.name = "Studio"
            viewModel.host = host
        }

        fun sentAuth(field: String): String =
            Frames
                .params(recorder.latest!!.sentFrames.first())
                .getValue(field)
                .jsonPrimitive.content
    }
}
