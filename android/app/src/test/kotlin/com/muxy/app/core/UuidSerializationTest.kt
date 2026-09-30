package com.muxy.app.core

import com.muxy.app.core.serialization.parseUuid
import com.muxy.app.core.serialization.uuidString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class UuidSerializationTest {
    @Test
    fun writesUppercaseLikeIos() {
        assertEquals("0000000A-0000-4000-8000-00000000000B", UUID.fromString("0000000a-0000-4000-8000-00000000000b").uuidString)
    }

    @Test
    fun parsesEitherCase() {
        val uuid = UUID.fromString("0000000a-0000-4000-8000-00000000000b")
        assertEquals(uuid, parseUuid("0000000A-0000-4000-8000-00000000000B"))
        assertEquals(uuid, parseUuid("0000000a-0000-4000-8000-00000000000b"))
    }

    @Test
    fun rejectsNonCanonicalText() {
        listOf("1-2-3-4-5", "", "not-a-uuid", "0000000a00004000800000000000000b").forEach { assertNull(it, parseUuid(it)) }
    }
}
