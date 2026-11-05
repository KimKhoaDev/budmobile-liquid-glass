package com.phimbud.mobile.core.crypto

/**
 * Handles AES-GCM-256 subtitle decryption for PhimBud across Android and iOS.
 *
 * Web Algorithm:
 * 1. ROT-19 on filename: (c - base + 19) % 26 + base
 * 2. Key = SHA-256("/watch/" + rot19(fileName))
 * 3. Base64 decode raw response
 * 4. First 12 bytes = IV
 * 5. Remaining bytes = Ciphertext + 16-byte Auth Tag
 * 6. Decrypt AES/GCM/NoPadding
 */
object SubtitleCipher {

    private val HTML_TAG_REGEX = Regex("<(?!/?[bi][\\s>])[^>]*>")
    private val ASS_TAG_REGEX = Regex("\\{[^}]+\\}")

    fun rot19(fileName: String): String = buildString(fileName.length) {
        for (ch in fileName) {
            when (ch) {
                in 'A'..'Z' -> {
                    val code = (ch.code - 65 + 19) % 26 + 65
                    append(code.toChar())
                }
                in 'a'..'z' -> {
                    val code = (ch.code - 97 + 19) % 26 + 97
                    append(code.toChar())
                }
                else -> append(ch)
            }
        }
    }

    fun deriveAesKey(fileName: String): ByteArray {
        val path = "/watch/" + rot19(fileName)
        return Sha256.digest(path.encodeToByteArray())
    }

    fun decrypt(fileName: String, base64Ciphertext: String): String {
        val keyBytes = deriveAesKey(fileName)
        val raw = UrlCipher.base64Decode(base64Ciphertext.trim())
        require(raw.size >= 28) { "Invalid subtitle payload size: ${raw.size}" }

        val iv = raw.copyOfRange(0, 12)
        val ciphertextWithTag = raw.copyOfRange(12, raw.size)

        val decryptedBytes = AesGcm256.decrypt(keyBytes, iv, ciphertextWithTag)
        var rawText = decryptedBytes.decodeToString()
        if (rawText.startsWith("\uFEFF")) {
            rawText = rawText.substring(1)
        }
        return cleanSubtitle(rawText)
    }

    fun cleanSubtitle(text: String): String {
        val strippedHtml = HTML_TAG_REGEX.replace(text, "")
        return ASS_TAG_REGEX.replace(strippedHtml, "")
    }
}
