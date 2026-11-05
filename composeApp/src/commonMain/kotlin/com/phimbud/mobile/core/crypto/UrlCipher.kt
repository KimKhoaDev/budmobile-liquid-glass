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
     * Pure Kotlin Base64 / Base64URL decode across Android, iOS, and JVM unit tests.
     */
    fun base64Decode(input: String): ByteArray {
        val sb = StringBuilder(input.length)
        for (c in input) {
            if (c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t') {
                sb.append(c)
            }
        }
        val clean = sb.toString()
        val len = clean.length
        if (len == 0) return ByteArray(0)
        val pad = when (len % 4) {
            2 -> "=="
            3 -> "="
            else -> ""
        }
        val full = clean + pad
        val out = ByteArray((full.length / 4) * 3)
        var outIdx = 0
        var i = 0
        while (i < full.length) {
            val c0 = b64CharToVal(full[i++])
            val c1 = b64CharToVal(full[i++])
            val c2 = if (i < full.length) b64CharToVal(full[i++]) else 64
            val c3 = if (i < full.length) b64CharToVal(full[i++]) else 64

            val b0 = (c0 shl 2) or (c1 shr 4)
            out[outIdx++] = b0.toByte()

            if (c2 != 64) {
                val b1 = ((c1 and 0xF) shl 4) or (c2 shr 2)
                out[outIdx++] = b1.toByte()
            }
            if (c3 != 64) {
                val b2 = ((c2 and 0x3) shl 6) or c3
                out[outIdx++] = b2.toByte()
            }
        }
        return if (outIdx == out.size) out else out.copyOf(outIdx)
    }

    private fun b64CharToVal(c: Char): Int = when (c) {
        in 'A'..'Z' -> c.code - 'A'.code
        in 'a'..'z' -> c.code - 'a'.code + 26
        in '0'..'9' -> c.code - '0'.code + 52
        '+', '-' -> 62
        '/', '_' -> 63
        else -> 64
    }

    fun base64UrlEncode(bytes: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
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
