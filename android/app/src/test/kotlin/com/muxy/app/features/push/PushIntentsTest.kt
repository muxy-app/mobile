package com.muxy.app.features.push

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushIntentsTest {
    private fun request(
        extras: Map<String, String>,
        action: String? = PushIntents.ACTION_OPEN,
        flags: Int = 0,
    ) = PushIntents.request(action, flags, extras::get)

    @Test
    fun theTapTargetSurvivesTheIntentExtras() {
        val extras = PushIntents.extras(PushVectors.target)
        assertEquals(
            mapOf(
                "server_id" to PushVectors.SERVER_ID,
                "project_id" to PushVectors.PROJECT_ID,
                "session_id" to "42",
                "event_id" to "1731067200000123",
            ),
            extras,
        )
        assertEquals(PushVectors.target, request(extras)?.target)
    }

    @Test
    fun largeIdsKeepEveryBit() {
        val target = PushTarget("server", ULong.MAX_VALUE, "project", ULong.MAX_VALUE - 1u)
        assertEquals(target, request(PushIntents.extras(target))?.target)
    }

    @Test
    fun notificationsThatCouldNotBeDecryptedOpenHome() {
        assertNull(checkNotNull(request(emptyMap())).target)
        val garbage = PushIntents.extras(PushVectors.target) + ("session_id" to "-1")
        assertNull(checkNotNull(request(garbage)).target)
        val missingServer = PushIntents.extras(PushVectors.target) + ("server_id" to "")
        assertNull(checkNotNull(request(missingServer)).target)
    }

    @Test
    fun otherIntentsAreNotNotificationTaps() {
        assertNull(request(PushIntents.extras(PushVectors.target), action = Intent.ACTION_MAIN))
        assertNull(request(PushIntents.extras(PushVectors.target), action = null))
    }

    @Test
    fun reopeningFromRecentsDoesNotReplayTheTap() {
        assertNull(request(PushIntents.extras(PushVectors.target), flags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
    }
}
