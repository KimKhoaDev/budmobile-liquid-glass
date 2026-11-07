package com.phimbud.mobile.core.crypto

/**
 * Handles ROT-6 and Base64 cipher operations used by PhimBud
 * to obfuscate HLS playlist AES-128 keys and media segments.
 *
 * Algorithm from PhimBud web:
 * Gt = e => atob(Kt(e, 6))
 * Kt = (e, t=13) => e.replace(/[a-zA-Z]/g, e => String.fromCharCode((e <= 'Z' ? 90 : 122) >= (e = e.charCodeAt(0) + t) ? e : e - 26))
 */
object UrlCipher {

    private const val B64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val B64_DECODE_TABLE = IntArray(128) { 64 }.apply {
        for (c in 'A'..'Z') this[c.code] = c.code - 'A'.code
        for (c in 'a'..'z') this[c.code] = c.code - 'a'.code + 26
        for (c in '0'..'9') this[c.code] = c.code - '0'.code + 52
        this['+'.code] = 62
        this['-'.code] = 62
        this['/'.code] = 63
        this['_'.code] = 63
        // Skip markers (-1) for padding & whitespace
        this['='.code] = -1
        this['\n'.code] = -1
        this['\r'.code] = -1
        this[' '.code] = -1
        this['\t'.code] = -1
    }

    fun rot(input: String, shift: Int = 6): String = buildString(input.length) {
        val s = ((shift % 26) + 26) % 26
        for (ch in input) {
            when (ch) {
                in 'A'..'Z' -> {
                    val code = ch.code + s
                    append((if (code <= 90) code else code - 26).toChar())
                }
                in 'a'..'z' -> {
                    val code = ch.code + s
                    append((if (code <= 122) code else code - 26).toChar())
                }
                else -> append(ch)
            }
        }
    }

    /**
     * Single-pass pure Kotlin Base64 / Base64URL decode across Android, iOS, and JVM unit tests
     * with zero intermediate String copies.
     */
    fun base64Decode(input: String): ByteArray {
        val inLen = input.length
        if (inLen == 0) return ByteArray(0)

        val out = ByteArray(((inLen + 3) shr 2) * 3)
        var outIdx = 0
        var q0 = 0
        var q1 = 0
        var q2 = 0
        var qCount = 0

        for (i in 0 until inLen) {
            val code = input[i].code
            val v = if (code < 128) B64_DECODE_TABLE[code] else 64
            if (v < 0) continue
            if (v >= 64) return ByteArray(0)

            when (qCount) {
                0 -> {
                    q0 = v
                    qCount = 1
                }
                1 -> {
                    q1 = v
                    qCount = 2
                }
                2 -> {
                    q2 = v
                    qCount = 3
                }
                else -> {
                    out[outIdx++] = ((q0 shl 2) or (q1 shr 4)).toByte()
                    out[outIdx++] = (((q1 and 0xF) shl 4) or (q2 shr 2)).toByte()
                    out[outIdx++] = (((q2 and 0x3) shl 6) or v).toByte()
                    qCount = 0
                }
            }
        }

        if (qCount == 1) {
            return ByteArray(0)
        } else if (qCount == 2) {
            out[outIdx++] = ((q0 shl 2) or (q1 shr 4)).toByte()
        } else if (qCount == 3) {
            out[outIdx++] = ((q0 shl 2) or (q1 shr 4)).toByte()
            out[outIdx++] = (((q1 and 0xF) shl 4) or (q2 shr 2)).toByte()
        }

        return if (outIdx == 0) {
            ByteArray(0)
        } else if (outIdx == out.size) {
            out
        } else {
            out.copyOf(outIdx)
        }
    }

    fun base64UrlEncode(bytes: ByteArray): String {
        val table = B64_URL_ALPHABET
        val sb = StringBuilder((bytes.size * 4 + 2) / 3)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i++].toInt() and 0xFF
            val b1 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1
            val b2 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1

            val c0 = b0 shr 2
            val c1 = ((b0 and 0x3) shl 4) or (if (b1 != -1) b1 shr 4 else 0)
            sb.append(table[c0])
            sb.append(table[c1])

            if (b1 != -1) {
                val c2 = ((b1 and 0xF) shl 2) or (if (b2 != -1) b2 shr 6 else 0)
                sb.append(table[c2])
            }
            if (b2 != -1) {
                val c3 = b2 and 0x3F
                sb.append(table[c3])
            }
        }
        return sb.toString()
    }

    /**
     * Decodes an obfuscated segment or key filename.
     * E.g. Hta3TgC2...Hq== -> 787fb64cabe26b3b2b8a5f29aa3fca80-...
     */
    fun decodeSegmentFilename(obfuscatedName: String): String? {
        return runCatching {
            val rot6 = rot(obfuscatedName, 6)
            val bytes = base64Decode(rot6)
            val decoded = bytes.decodeToString()
            // Valid segment and key filenames are printable ASCII strings without replacement chars
            if (decoded.isNotEmpty() && decoded.all { it.code in 0x20..0x7E }) {
                decoded
            } else {
                null
            }
        }.getOrNull()
    }
}
