package com.phimbud.mobile.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

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
        val hex = key.joinToString("") { b ->
            ((b.toInt() and 0xFF).toString(16)).padStart(2, '0')
        }
        assertEquals("ffd25210367ed65a537e8c778154b6aba7ac31c79870b93cea1aa5b28777b754", hex)
    }

    @Test
    fun testCleanSubtitlePreservesItalicBoldAndStripsAssAndFontTags() {
        val raw = "{\\an8}<font color=\"#ffff00\"><i>Xin chào</i> <b>PhimBud</b></font>"
        assertEquals("<i>Xin chào</i> <b>PhimBud</b>", SubtitleCipher.cleanSubtitle(raw))
    }
}
