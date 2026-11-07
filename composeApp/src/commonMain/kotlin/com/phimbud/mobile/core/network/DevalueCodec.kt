package com.phimbud.mobile.core.network

import com.phimbud.mobile.core.crypto.UrlCipher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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

    /**
     * Resolves a flattened SvelteKit devalue array into a regular tree-like JsonElement.
     */
    fun unflatten(pool: JsonArray, rootIndex: Int = 0): JsonElement {
        val resolved = mutableMapOf<Int, JsonElement>()
        val resolving = mutableSetOf<Int>()

        fun resolve(index: Int): JsonElement {
            if (index < 0) {
                return when (index) {
                    -1 -> JsonNull
                    else -> JsonPrimitive(index)
                }
            }
            if (index >= pool.size) return JsonNull
            if (index in resolved) return resolved[index]!!
            if (index in resolving) return JsonNull // break circular reference

            resolving.add(index)
            val item = pool[index]
            val result: JsonElement = when (item) {
                is JsonPrimitive -> item
                is JsonArray -> {
                    // Check special devalue markers like ["Date", "2026-..."]
                    if (item.size == 2 && item[0] is JsonPrimitive && item[0].jsonPrimitive.content == "Date") {
                        item[1]
                    } else {
                        val elements = item.map { elem ->
                            if (elem is JsonPrimitive && !elem.isString && elem.booleanOrNull == null) {
                                val targetIdx = elem.content.toIntOrNull()
                                if (targetIdx != null) resolve(targetIdx) else elem
                            } else {
                                elem
                            }
                        }
                        JsonArray(elements)
                    }
                }
                is JsonObject -> {
                    val map = mutableMapOf<String, JsonElement>()
                    for ((k, v) in item) {
                        val targetIdx = if (v is JsonPrimitive && !v.isString && v.booleanOrNull == null) {
                            v.content.toIntOrNull()
                        } else null

                        map[k] = if (targetIdx != null) resolve(targetIdx) else v
                    }
                    JsonObject(map)
                }
                else -> JsonNull
            }
            resolving.remove(index)
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
