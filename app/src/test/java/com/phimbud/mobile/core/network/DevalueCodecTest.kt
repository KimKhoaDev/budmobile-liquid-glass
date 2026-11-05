package com.phimbud.mobile.core.network

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DevalueCodecTest {

    @Test
    fun testUnflattenSimple() {
        // [{"fid": 1, "name": 2}, "mem3", "Doctor X"]
        val pool = JsonArray(listOf(
            JsonObject(mapOf("fid" to JsonPrimitive(1), "name" to JsonPrimitive(2))),
            JsonPrimitive("mem3"),
            JsonPrimitive("Doctor X")
        ))

        val resolved = DevalueCodec.unflatten(pool) as JsonObject
        assertEquals("mem3", resolved["fid"]?.jsonPrimitive?.content)
        assertEquals("Doctor X", resolved["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun testEncodeVideoSrc() {
        val encoded = DevalueCodec.encodeVideoSrc("9y9C", "1")
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }
}
