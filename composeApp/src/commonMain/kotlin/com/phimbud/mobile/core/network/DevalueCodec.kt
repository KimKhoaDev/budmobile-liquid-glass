package com.phimbud.mobile.core.network

import com.phimbud.mobile.core.crypto.UrlCipher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Encodes and decodes SvelteKit devalue-flattened data payloads.
 */
object DevalueCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private const val NO_POOL_INDEX = Int.MIN_VALUE

    private fun parsePoolIndexOrSentinel(elem: JsonElement): Int {
        if (elem !is JsonPrimitive || elem.isString) return NO_POOL_INDEX
        val s = elem.content
        val len = s.length
        if (len == 0 || len > 11) return NO_POOL_INDEX

        var neg = false
        var idx = 0
        val first = s[0]
        if (first == '-') {
            if (len == 1) return NO_POOL_INDEX
            neg = true
            idx = 1
        } else if (first !in '0'..'9') {
            return NO_POOL_INDEX
        }

        var acc = 0L
        while (idx < len) {
            val c = s[idx]
            if (c !in '0'..'9') return NO_POOL_INDEX
            acc = acc * 10L + (c.code - '0'.code)
            if (acc > Int.MAX_VALUE.toLong()) return NO_POOL_INDEX
            idx++
        }
        return if (neg) (-acc).toInt() else acc.toInt()
    }

    /**
     * Resolves a flattened SvelteKit devalue array into a regular tree-like JsonElement
     * using primitive arrays (zero Int boxing or HashMap hashing).
     */
    fun unflatten(pool: JsonArray, rootIndex: Int = 0): JsonElement {
        val poolSize = pool.size
        if (poolSize == 0) return JsonNull

        val resolved = arrayOfNulls<JsonElement>(poolSize)
        val resolving = BooleanArray(poolSize)

        fun resolve(index: Int): JsonElement {
            if (index < 0) {
                return when (index) {
                    -1, -2, -3, -4, -5 -> JsonNull
                    -6 -> JsonPrimitive(0)
                    else -> JsonPrimitive(index)
                }
            }
            if (index >= poolSize) return JsonNull
            resolved[index]?.let { return it }
            if (resolving[index]) return JsonNull // break circular reference

            resolving[index] = true
            val item = pool[index]
            val result: JsonElement = when (item) {
                is JsonPrimitive -> item
                is JsonArray -> {
                    // Check special devalue markers like ["Date", "2026-..."]
                    if (item.size == 2 && item[0] is JsonPrimitive && item[0].jsonPrimitive.content == "Date") {
                        item[1]
                    } else {
                        val list = ArrayList<JsonElement>(item.size)
                        for (i in 0 until item.size) {
                            val elem = item[i]
                            val targetIdx = parsePoolIndexOrSentinel(elem)
                            list.add(if (targetIdx != NO_POOL_INDEX) resolve(targetIdx) else elem)
                        }
                        JsonArray(list)
                    }
                }
                is JsonObject -> {
                    val map = LinkedHashMap<String, JsonElement>(item.size)
                    for ((k, v) in item) {
                        val targetIdx = parsePoolIndexOrSentinel(v)
                        map[k] = if (targetIdx != NO_POOL_INDEX) resolve(targetIdx) else v
                    }
                    JsonObject(map)
                }
            }
            resolving[index] = false
            resolved[index] = result
            return result
        }

        return resolve(rootIndex)
    }

    fun extractNodeData(jsonString: String, nodeIndex: Int = 2): JsonElement? {
        return runCatching {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val nodes = root["nodes"]?.jsonArray ?: return null
            if (nodeIndex >= nodes.size) return null
            val node = nodes[nodeIndex]
            if (node is JsonNull) return null
            val nodeObj = node.jsonObject
            val dataArray = nodeObj["data"]?.jsonArray ?: return null
            unflatten(dataArray)
        }.getOrNull()
    }

    fun extractRpcResult(jsonString: String): JsonElement? {
        return runCatching {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val dataStr = root["data"]?.jsonPrimitive?.content ?: return null
            val pool = json.parseToJsonElement(dataStr).jsonArray
            if (pool.size > 1) {
                unflatten(pool, 1)
            } else {
                unflatten(pool, 0)
            }
        }.getOrNull()
    }

    // Encoding helpers for remote RPCs
    fun encodeSingleArg(arg: String): String {
        val payload = "[\"$arg\"]"
        return UrlCipher.base64UrlEncode(payload.encodeToByteArray())
    }

    fun encodeVideoSrc(fid: String, server: String): String {
        val payload = "[{\"fid\":1,\"server\":2},\"$fid\",\"$server\"]"
        return UrlCipher.base64UrlEncode(payload.encodeToByteArray())
    }

    fun encodePlaytime(fid: String, seconds: Int): String {
        val payload = "[{\"fid\":1,\"time\":2},\"$fid\",$seconds]"
        return UrlCipher.base64UrlEncode(payload.encodeToByteArray())
    }

    fun encodeCollection(fid: String, status: String): String = encodeAddToCollection(fid, status)

    fun encodeAddToCollection(fid: String, status: String): String {
        val payload = if (status.isBlank()) {
            "[{\"fid\":1,\"status\":-1},\"$fid\"]"
        } else {
            "[{\"fid\":1,\"status\":2},\"$fid\",\"$status\"]"
        }
        return UrlCipher.base64UrlEncode(payload.encodeToByteArray())
    }
}
