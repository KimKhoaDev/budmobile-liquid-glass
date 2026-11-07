package com.phimbud.mobile.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cross-verifies our pure-Kotlin multiplatform [Sha256], [AesGcm256], and [SubtitleCipher]
 * against Java's reference JCE (MessageDigest SHA-256 and Cipher AES/GCM/NoPadding).
 */
class AesGcm256JvmCrossVerifyTest {

    @Test
    fun `Sha256 matches java MessageDigest across arbitrary payload sizes`() {
        val jceSha = MessageDigest.getInstance("SHA-256")
        val rng = Random(42)
        for (len in listOf(0, 1, 3, 55, 56, 57, 63, 64, 65, 128, 1000)) {
            val data = rng.nextBytes(len)
            val expected = jceSha.digest(data)
            val actual = Sha256.digest(data)
            assertArrayEquals("Mismatch for SHA-256 length $len", expected, actual)
        }
    }

    @Test
    fun `AesGcm256 decrypts JCE AES-GCM-256 ciphertexts across all block boundaries`() {
        val rng = Random(2026)
        for (len in listOf(0, 1, 15, 16, 17, 31, 32, 33, 255, 4096)) {
            val key = rng.nextBytes(32)
            val iv = rng.nextBytes(12)
            val plaintext = rng.nextBytes(len)

            val jce = Cipher.getInstance("AES/GCM/NoPadding")
            jce.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val ciphertextWithTag = jce.doFinal(plaintext)

            val decrypted = AesGcm256.decrypt(key, iv, ciphertextWithTag)
            assertArrayEquals("Mismatch for AES-GCM length $len", plaintext, decrypted)
        }
    }

    @Test
    fun `AesGcm256 rejects tampered ciphertext or authentication tag`() {
        val rng = Random(99)
        val key = rng.nextBytes(32)
        val iv = rng.nextBytes(12)
        val plaintext = "Xin chào Việt Nam".toByteArray(Charsets.UTF_8)

        val jce = Cipher.getInstance("AES/GCM/NoPadding")
        jce.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ciphertextWithTag = jce.doFinal(plaintext)

        // Flip 1 bit in auth tag
        val tamperedTag = ciphertextWithTag.copyOf()
        tamperedTag[tamperedTag.lastIndex] = (tamperedTag.last().toInt() xor 0x01).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            AesGcm256.decrypt(key, iv, tamperedTag)
        }

        // Flip 1 bit in ciphertext body
        val tamperedBody = ciphertextWithTag.copyOf()
        tamperedBody[0] = (tamperedBody[0].toInt() xor 0x80).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            AesGcm256.decrypt(key, iv, tamperedBody)
        }
    }

    @Test
    fun `SubtitleCipher end-to-end decrypts real Vietnamese SRT encrypted with JCE`() {
        val fileName = "squid_game_s02e01_vi.srt"
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(("/watch/" + SubtitleCipher.rot19(fileName)).toByteArray(Charsets.UTF_8))
        val iv = ByteArray(12) { (it + 7).toByte() }
        val rawSrt = "\uFEFF1\n00:00:02,000 --> 00:00:05,000\n{\\an8}<font color=\"#fff\"><i>Trò chơi bắt đầu!</i></font>\n"

        val jce = Cipher.getInstance("AES/GCM/NoPadding")
        jce.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val cipherBytes = jce.doFinal(rawSrt.toByteArray(Charsets.UTF_8))

        val payload = iv + cipherBytes
        val base64Payload = Base64.getEncoder().encodeToString(payload)

        val result = SubtitleCipher.decrypt(fileName, base64Payload)
        assertEquals("1\n00:00:02,000 --> 00:00:05,000\n<i>Trò chơi bắt đầu!</i>\n", result)
    }

    @Test
    fun `AesGcm256 decrypts in-place slice with non-zero inputOffset and trailing padding`() {
        val rng = Random(777)
        val key = rng.nextBytes(32)
        val iv = rng.nextBytes(12)
        val plaintext = rng.nextBytes(513)

        val jce = Cipher.getInstance("AES/GCM/NoPadding")
        jce.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ciphertextWithTag = jce.doFinal(plaintext)

        val prefix = rng.nextBytes(19)
        val suffix = rng.nextBytes(23)
        val combined = prefix + ciphertextWithTag + suffix

        val decrypted = AesGcm256.decrypt(
            key = key,
            iv = iv,
            ciphertextWithTag = combined,
            inputOffset = prefix.size,
            inputLength = ciphertextWithTag.size
        )
        assertArrayEquals(plaintext, decrypted)
    }
}
