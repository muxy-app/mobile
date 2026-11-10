package com.muxy.app.features.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushCryptoTest {
    @Test
    fun theKeyIdMatchesTheVector() {
        assertEquals("627df844ff14f4ed", PushVectors.key.keyId.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun collapseAndThreadIdsMatchTheVectors() {
        assertEquals("usZ3J1QStv6L6omf", PushVectors.key.collapseId(42u))
        assertEquals("aJnt975IClwhFwRP", PushVectors.key.threadId(PushVectors.PROJECT_ID))
    }

    @Test
    fun sealingTheVectorReproducesThePayloadByteForByte() {
        assertEquals(PushVectors.PAYLOAD, PushVectors.seal(PushVectors.PLAINTEXT))
    }

    @Test
    fun theVectorPayloadDecrypts() {
        val message = checkNotNull(PushOpener.open(PushVectors.PAYLOAD, listOf(PushVectors.key)))
        assertEquals(
            PushMessage(
                version = 1,
                serverId = PushVectors.SERVER_ID,
                projectId = PushVectors.PROJECT_ID,
                sessionId = 42u,
                eventId = 1_731_067_200_000_123u,
                kind = "attention",
                title = "Claude Code · muxy-website",
                body = "Needs your attention",
                timestamp = 1_731_067_200,
            ),
            message,
        )
        assertEquals(PushVectors.target, message.target)
    }

    @Test
    fun anotherServerIdFailsToAuthenticate() {
        val other = checkNotNull(PushKey.of("other", PushVectors.keyBytes))
        assertNull(PushOpener.open(PushVectors.PAYLOAD, listOf(other)))
    }

    @Test
    fun theKeyIsFoundByItsKeyIdAmongSeveral() {
        val unrelated = checkNotNull(PushKey.of("server-a", ByteArray(32) { 9 }))
        val another = checkNotNull(PushKey.of("server-b", ByteArray(32) { 3 }))
        assertEquals(PushVectors.SERVER_ID, PushOpener.open(PushVectors.PAYLOAD, listOf(unrelated, PushVectors.key, another))?.serverId)
    }

    @Test
    fun anUnknownKeyIdOpensNothing() {
        val unrelated = checkNotNull(PushKey.of(PushVectors.SERVER_ID, ByteArray(32) { 9 }))
        assertNull(PushOpener.open(PushVectors.PAYLOAD, listOf(unrelated)))
        assertNull(PushOpener.open(PushVectors.PAYLOAD, emptyList()))
    }

    @Test
    fun aMessageNamingAnotherServerIsRejected() {
        assertNull(PushOpener.open(PushVectors.seal(PushVectors.message(serverId = "someone-else")), listOf(PushVectors.key)))
    }

    @Test
    fun otherPlaintextVersionsAreRejected() {
        assertNull(PushOpener.open(PushVectors.seal(PushVectors.message(version = 2)), listOf(PushVectors.key)))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        assertEquals("Finished working", PushOpener.open(PushVectors.seal(PushVectors.message()), listOf(PushVectors.key))?.body)
    }

    @Test
    fun malformedPayloadsOpenNothing() {
        val payload = PushVectors.PAYLOAD
        val tampered = payload.dropLast(1) + if (payload.endsWith("A")) "B" else "A"
        val otherVersion = "Ag" + payload.drop(2)
        listOf("", "not base64!", payload.take(40), tampered, otherVersion, "$payload=").forEach {
            assertNull(it, PushOpener.open(it, listOf(PushVectors.key)))
        }
    }

    @Test
    fun keysMustBeThirtyTwoBytesAndNamed() {
        assertNull(PushKey.of(PushVectors.SERVER_ID, ByteArray(31)))
        assertNull(PushKey.of("", PushVectors.keyBytes))
    }

    @Test
    fun keysNeverDescribeTheirBytes() {
        assertEquals("PushKey(${PushVectors.SERVER_ID})", PushVectors.key.toString())
    }
}
