package com.phimbud.mobile.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DevalueCodecTest {

    @Test
    fun testUnflattenSimple() {
        val pool = JsonArray(
            listOf(
                JsonObject(mapOf("fid" to JsonPrimitive(1), "name" to JsonPrimitive(2))),
                JsonPrimitive("mem3"),
                JsonPrimitive("Doctor X")
            )
        )

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
