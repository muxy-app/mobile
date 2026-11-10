package com.muxy.app.features.push

import com.muxy.app.features.server.PushIntent
import com.muxy.app.models.ConnectionKind
import com.muxy.app.testing.FakeNotificationPermission
import com.muxy.app.testing.InMemoryConnectionStore
import com.muxy.app.testing.InMemoryPushKeyStore
import com.muxy.app.testing.InMemoryPushStateStore
import com.muxy.app.testing.RecordingPushNotifications
import com.muxy.app.testing.device
import com.muxy.app.testing.fakePushCenter
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.muxy_mobile.PushRegistration
import uniffi.muxy_mobile.PushStatus

class PushCenterTest {
    private val studio = device(name = "Studio").copy(kind = ConnectionKind.SERVER, serverId = PushVectors.SERVER_ID)

    private class Setup(
        val push: PushCenter,
        val keys: InMemoryPushKeyStore,
        val notifications: RecordingPushNotifications,
        val permission: FakeNotificationPermission,
        val store: InMemoryPushStateStore,
    )

    private fun TestScope.setup(
        permission: FakeNotificationPermission = FakeNotificationPermission(),
        start: Boolean = true,
    ): Setup {
        val keys = InMemoryPushKeyStore().apply { this.keys[PushVectors.SERVER_ID] = PushVectors.key }
        val notifications = RecordingPushNotifications()
        val store = InMemoryPushStateStore()
        val push =
            fakePushCenter(
                backgroundScope,
                connections = InMemoryConnectionStore(listOf(studio)),
                store = store,
                keys = keys,
                permission = permission,
                notifications = notifications,
            )
        if (start) push.start()
        runCurrent()
        return Setup(push, keys, notifications, permission, store)
    }

    @Test
    fun deliveredNotificationsAreDecryptedAndPosted() =
        runTest {
            val setup = setup()
            setup.push.deliver(PushVectors.data())
            val posted = setup.notifications.posted.single()
            assertEquals("Claude Code · muxy-website", posted.alert.title)
            assertEquals(PushVectors.target, posted.target)
        }

    @Test
    fun theBannerIsSkippedWhileConnectedToThatServerInTheForeground() =
        runTest {
            val setup = setup()
            setup.push.isConnectedInForeground = { it == PushVectors.SERVER_ID }
            setup.push.deliver(PushVectors.data())
            assertTrue(setup.notifications.posted.isEmpty())
        }

    @Test
    fun aServerTurnedOffLocallyOnlyShowsTheFallback() =
        runTest {
            val setup = setup()
            setup.push.setEnabled(PushVectors.SERVER_ID, false)
            runCurrent()
            setup.push.deliver(PushVectors.data())
            val posted = setup.notifications.posted.single()
            assertEquals("An agent needs your attention", posted.alert.body)
            assertNull(posted.target)
        }

    @Test
    fun aPausedServerKeepsItsKeyAndANewKeyReplacesIt() =
        runTest {
            val setup = setup()
            setup.push.record(PushRegistration(PushStatus.DISABLED, null), PushVectors.SERVER_ID)
            assertTrue(setup.push.hasKey(PushVectors.SERVER_ID))
            assertTrue(setup.push.isRegistered(PushVectors.SERVER_ID))
            setup.push.deliver(PushVectors.data())
            assertEquals(
                PushVectors.target,
                setup.notifications.posted
                    .last()
                    .target,
            )

            setup.push.record(PushRegistration(PushStatus.ACTIVE, ByteArray(32) { 4 }), PushVectors.SERVER_ID)
            setup.push.deliver(PushVectors.data())
            assertNull(
                setup.notifications.posted
                    .last()
                    .target,
            )
        }

    @Test
    fun unregisteringDropsTheKeyAndTheRegistration() =
        runTest {
            val setup = setup()
            setup.push.record(PushRegistration(PushStatus.ACTIVE, PushVectors.keyBytes), PushVectors.SERVER_ID)
            setup.push.recordUnregistered(PushVectors.SERVER_ID)
            assertFalse(setup.push.hasKey(PushVectors.SERVER_ID))
            assertFalse(setup.push.isRegistered(PushVectors.SERVER_ID))
        }

    @Test
    fun unsupportedServersAreNotRegistered() =
        runTest {
            val setup = setup()
            setup.push.recordUnsupported(PushVectors.SERVER_ID)
            assertFalse(setup.push.isRegistered(PushVectors.SERVER_ID))
        }

    @Test
    fun nothingIsDecidedUntilPermissionIsKnown() =
        runTest {
            val setup = setup(start = false)
            assertEquals(PushIntent.UNDECIDED, setup.push.pushIntent(PushVectors.SERVER_ID))
            setup.push.start()
            runCurrent()
            assertEquals(PushIntent.WANTED, setup.push.pushIntent(PushVectors.SERVER_ID))
        }

    @Test
    fun acknowledgingCancelsTheSessionsNotification() =
        runTest {
            val setup = setup()
            setup.push.removeDeliveredNotifications(42u, PushVectors.SERVER_ID)
            assertEquals(listOf("usZ3J1QStv6L6omf"), setup.notifications.cancelled)
        }

    @Test
    fun notificationsNeedPermissionAndTheServerToggle() =
        runTest {
            val permission = FakeNotificationPermission(isGranted = false, needsRuntimeRequest = true)
            val setup = setup(permission)
            assertEquals(PushAuthorization.NOT_DETERMINED, setup.push.authorization.value)
            assertEquals(PushIntent.UNDECIDED, setup.push.pushIntent(PushVectors.SERVER_ID))
            permission.isGranted = true
            permission.needsRuntimeRequest = false
            setup.push.onPermissionResult()
            runCurrent()
            assertEquals(PushAuthorization.ALLOWED, setup.push.authorization.value)
            assertEquals(PushIntent.WANTED, setup.push.pushIntent(PushVectors.SERVER_ID))
            assertEquals("fcm-token", setup.push.deviceToken)
            setup.push.setEnabled(PushVectors.SERVER_ID, false)
            runCurrent()
            assertEquals(PushIntent.UNWANTED, setup.push.pushIntent(PushVectors.SERVER_ID))
        }

    @Test
    fun aDeclinedPermissionIsDenied() =
        runTest {
            val setup = setup(FakeNotificationPermission(isGranted = false, needsRuntimeRequest = true))
            setup.push.onPermissionResult()
            runCurrent()
            assertEquals(PushAuthorization.DENIED, setup.push.authorization.value)
            assertEquals(PushIntent.UNWANTED, setup.push.pushIntent(PushVectors.SERVER_ID))
            assertNull(setup.push.deviceToken)
        }

    @Test
    fun aNewTokenReachesConnectedServers() =
        runTest {
            val setup = setup()
            var changes = 0
            setup.push.contextDidChange = { changes += 1 }
            setup.push.onNewToken("replacement")
            runCurrent()
            assertEquals("replacement", setup.push.deviceToken)
            assertEquals(1, changes)
        }

    @Test
    fun forgettingAServerDropsItsKeyAndPreferences() =
        runTest {
            val setup = setup()
            setup.push.setEnabled(PushVectors.SERVER_ID, false)
            runCurrent()
            setup.push.forget(PushVectors.SERVER_ID)
            assertFalse(setup.push.hasKey(PushVectors.SERVER_ID))
            assertTrue(
                setup.store.state.value
                    ?.isEnabled(PushVectors.SERVER_ID) == true,
            )
        }
}
