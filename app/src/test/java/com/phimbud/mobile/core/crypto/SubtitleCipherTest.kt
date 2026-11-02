package com.phimbud.mobile.core.crypto

import org.junit.Assert.*
import org.junit.Test

class SubtitleCipherTest {

    @Test
    fun testRot19() {
        val fileName = "ep01_vi.srt"
        val rotated = SubtitleCipher.rot19(fileName)
        assertEquals("xi01_ob.lkm", rotated)
    }

    @Test
    fun testDeriveKey() {
        val fileName = "ep01_vi.srt"
        val key = SubtitleCipher.deriveAesKey(fileName)
        val hex = key.joinToString("") { "%02x".format(it) }
        assertEquals("ffd25210367ed65a537e8c778154b6aba7ac31c79870b93cea1aa5b28777b754", hex)
    }
}
