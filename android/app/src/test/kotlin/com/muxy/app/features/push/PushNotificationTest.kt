package com.muxy.app.features.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PushNotificationTest {
    private val studio = mapOf(PushVectors.SERVER_ID to "Studio")

    @Test
    fun authenticatedPayloadsShowTheirTextAndCarryTheTarget() {
        val notification = checkNotNull(PushNotification.from(PushVectors.data(), listOf(PushVectors.key), studio))
        assertEquals(PushAlert("Claude Code · muxy-website", "Needs your attention"), notification.alert)
        assertEquals(PushKind.ATTENTION, notification.kind)
        assertEquals("usZ3J1QStv6L6omf", notification.tag)
        assertEquals("aJnt975IClwhFwRP", notification.group)
        assertEquals(PushVectors.target, notification.target)
    }

    @Test
    fun theServerNameIsAddedWhenSeveralServersArePaired() {
        val servers = studio + ("laptop" to "Laptop")
        assertEquals("Studio", PushNotification.from(PushVectors.data(), listOf(PushVectors.key), servers)?.alert?.subText)
    }

    @Test
    fun payloadsWithoutAKeyShowTheFallbackAndOpenHome() {
        val notification = checkNotNull(PushNotification.from(PushVectors.data(), emptyList(), studio))
        assertEquals(PushAlert("Muxy", "An agent needs your attention"), notification.alert)
        assertNull(notification.target)
        assertEquals("usZ3J1QStv6L6omf", notification.tag)
    }

    @Test
    fun forgedPayloadsShowTheFallbackAndNeverTheirBytes() {
        val forged = PushVectors.seal(PushVectors.message(), keyBytes = ByteArray(32) { 5 })
        val notification = checkNotNull(PushNotification.from(PushVectors.data(payload = forged), listOf(PushVectors.key), studio))
        assertEquals(PushAlert("Muxy", "An agent needs your attention"), notification.alert)
        assertFalse(notification.alert.title.contains(forged) || notification.alert.body.contains(forged))
        assertNull(notification.target)
    }

    @Test
    fun otherVersionsShowTheFallback() {
        val sealed = PushVectors.seal(PushVectors.message(version = 2))
        val notification = checkNotNull(PushNotification.from(PushVectors.data(payload = sealed), listOf(PushVectors.key), studio))
        assertEquals("An agent needs your attention", notification.alert.body)
        assertNull(notification.target)
    }

    @Test
    fun theKindPicksTheChannelAndTheFallbackWhenDataHasNone() {
        val data = PushVectors.data(payload = "garbage", kind = "completed") - "title" - "body"
        val notification = checkNotNull(PushNotification.from(data, emptyList(), studio))
        assertEquals(PushKind.COMPLETED, notification.kind)
        assertEquals(PushAlert("Muxy", "An agent finished working"), notification.alert)
    }

    @Test
    fun notificationsWithoutCollapseOrThreadIdsAreStillShown() {
        val notification =
            checkNotNull(PushNotification.from(PushVectors.data() - "collapse_id" - "thread_id", listOf(PushVectors.key), studio))
        assertNull(notification.tag)
        assertNull(notification.group)
    }

    @Test
    fun messagesFromAnotherVersionAreIgnored() {
        assertNull(PushNotification.from(PushVectors.data() + ("muxy_v" to "2"), listOf(PushVectors.key), studio))
        assertNull(PushNotification.from(emptyMap(), listOf(PushVectors.key), studio))
    }
}
