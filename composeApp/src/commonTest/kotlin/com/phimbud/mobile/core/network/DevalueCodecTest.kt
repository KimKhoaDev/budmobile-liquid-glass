package com.phimbud.mobile.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

    @Test
    fun testEncodeAddToCollectionWishlist() {
        val encoded = DevalueCodec.encodeAddToCollection("mem3", "wishlist")
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun testEncodeAddToCollectionRemove() {
        val encoded = DevalueCodec.encodeAddToCollection("mem3", "")
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun testUnflattenHandlesSentinelsFloatsNestedArraysAndCycles() {
        val pool = JsonArray(
            listOf(
                JsonObject(
                    mapOf(
                        "rating" to JsonPrimitive(1),
                        "empty" to JsonPrimitive(-1),
                        "tags" to JsonPrimitive(2),
                        "self" to JsonPrimitive(0)
                    )
                ),
                JsonPrimitive(8.7),
                JsonArray(listOf(JsonPrimitive(3), JsonPrimitive(-4))),
                JsonPrimitive("Sci-Fi")
            )
        )

        val resolved = DevalueCodec.unflatten(pool).jsonObject
        assertEquals(8.7, resolved["rating"]?.jsonPrimitive?.double)
        assertEquals(JsonNull, resolved["empty"])
        val tags = resolved["tags"]!!.jsonArray
        assertEquals("Sci-Fi", tags[0].jsonPrimitive.content)
        assertEquals(JsonNull, tags[1])
        assertEquals(JsonNull, resolved["self"])
    }
}
