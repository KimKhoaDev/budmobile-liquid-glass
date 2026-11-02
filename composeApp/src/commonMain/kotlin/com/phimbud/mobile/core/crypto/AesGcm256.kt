package com.phimbud.mobile.core.crypto

/**
 * Pure Kotlin FIPS 197 AES-256 + NIST SP 800-38D Galois/Counter Mode (AES-256-GCM) decryption.
 * Supports 32-byte keys (256-bit), 12-byte IVs (96-bit), and 16-byte authentication tags (128-bit).
 */
object AesGcm256 {

    private val SBOX = intArrayOf(
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
        0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
        0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
        0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
        0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
        0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
        0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
        0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
        0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
        0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
        0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
        0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
        0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
        0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
        0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
    )

    private val RCON = intArrayOf(
        0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x1b, 0x36
    )

    fun decrypt(key: ByteArray, iv: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        require(key.size == 32) { "AES-256 key must be 32 bytes, got ${key.size}" }
        require(iv.size == 12) { "GCM IV must be 12 bytes, got ${iv.size}" }
        require(ciphertextWithTag.size >= 16) { "Ciphertext with tag must be at least 16 bytes" }

        val roundKeys = expandKey256(key)
        val cipherLen = ciphertextWithTag.size - 16

        // 1. Hash subkey H = AES_K(0^128)
        val zeroBlock = ByteArray(16)
        val hBlock = ByteArray(16)
        encryptBlock(roundKeys, zeroBlock, 0, hBlock, 0)
        val hHi = readBigEndianLong(hBlock, 0)
        val hLo = readBigEndianLong(hBlock, 8)

        // 2. Initial counter block J0 = IV || 0x00000001
        val j0 = ByteArray(16)
        iv.copyInto(j0, 0, 0, 12)
        j0[15] = 1
        val tagMask = ByteArray(16)
        encryptBlock(roundKeys, j0, 0, tagMask, 0)

        // 3. GHASH_H(AAD = empty, C = ciphertextWithTag[0 until cipherLen])
        var yHi = 0L
        var yLo = 0L
        val partial = ByteArray(16)

        var offset = 0
        while (offset + 16 <= cipherLen) {
            yHi = yHi xor readBigEndianLong(ciphertextWithTag, offset)
            yLo = yLo xor readBigEndianLong(ciphertextWithTag, offset + 8)
            val prodHiLo = ghashMultiply(yHi, yLo, hHi, hLo)
            yHi = prodHiLo.first
            yLo = prodHiLo.second
            offset += 16
        }

        val rem = cipherLen - offset
        if (rem > 0) {
            partial.fill(0)
            ciphertextWithTag.copyInto(partial, 0, offset, cipherLen)
            yHi = yHi xor readBigEndianLong(partial, 0)
            yLo = yLo xor readBigEndianLong(partial, 8)
            val prodHiLo = ghashMultiply(yHi, yLo, hHi, hLo)
            yHi = prodHiLo.first
            yLo = prodHiLo.second
        }

        // Length block: [len(AAD)_bits = 0 (64-bit) || len(C)_bits (64-bit)]
        val cipherBits = cipherLen.toLong() * 8L
        yLo = yLo xor cipherBits
        val finalProd = ghashMultiply(yHi, yLo, hHi, hLo)
        yHi = finalProd.first
        yLo = finalProd.second

        val computedTag = ByteArray(16)
        writeBigEndianLong(yHi xor readBigEndianLong(tagMask, 0), computedTag, 0)
        writeBigEndianLong(yLo xor readBigEndianLong(tagMask, 8), computedTag, 8)

        // Constant-time authentication tag verification
        var diff = 0
        for (i in 0 until 16) {
            diff = diff or ((computedTag[i].toInt() xor ciphertextWithTag[cipherLen + i].toInt()) and 0xFF)
        }
        if (diff != 0) {
            throw IllegalArgumentException("AES-GCM authentication tag verification failed")
        }

        // 4. GCTR_K(inc32(J0), C)
        val plaintext = ByteArray(cipherLen)
        val counterBlock = ByteArray(16)
        iv.copyInto(counterBlock, 0, 0, 12)
        val keystream = ByteArray(16)

        var blockIndex = 0
        var pos = 0
        while (pos < cipherLen) {
            val counter = 2 + blockIndex
            counterBlock[12] = (counter ushr 24).toByte()
            counterBlock[13] = (counter ushr 16).toByte()
            counterBlock[14] = (counter ushr 8).toByte()
            counterBlock[15] = counter.toByte()

            encryptBlock(roundKeys, counterBlock, 0, keystream, 0)
            val chunk = minOf(16, cipherLen - pos)
            for (j in 0 until chunk) {
                plaintext[pos + j] = (ciphertextWithTag[pos + j].toInt() xor keystream[j].toInt()).toByte()
            }
            pos += chunk
            blockIndex++
        }

        return plaintext
    }

    private fun ghashMultiply(xHi: Long, xLo: Long, hHi: Long, hLo: Long): Pair<Long, Long> {
        var zHi = 0L
        var zLo = 0L
        var vHi = hHi
        var vLo = hLo
        val polyR = -0x1f00000000000000L // 0xE100000000000000UL.toLong()

        for (i in 0 until 64) {
            if (((xHi ushr (63 - i)) and 1L) != 0L) {
                zHi = zHi xor vHi
                zLo = zLo xor vLo
            }
            val lsb = vLo and 1L
            vLo = (vLo ushr 1) or (vHi shl 63)
            vHi = vHi ushr 1
            if (lsb != 0L) {
                vHi = vHi xor polyR
            }
        }
        for (i in 0 until 64) {
            if (((xLo ushr (63 - i)) and 1L) != 0L) {
                zHi = zHi xor vHi
                zLo = zLo xor vLo
            }
            val lsb = vLo and 1L
            vLo = (vLo ushr 1) or (vHi shl 63)
            vHi = vHi ushr 1
            if (lsb != 0L) {
                vHi = vHi xor polyR
            }
        }
        return Pair(zHi, zLo)
    }

    private fun expandKey256(key: ByteArray): IntArray {
        val w = IntArray(60)
        for (i in 0 until 8) {
            w[i] = readBigEndianInt(key, i * 4)
        }
        for (i in 8 until 60) {
            var temp = w[i - 1]
            if (i % 8 == 0) {
                val rot = (temp shl 8) or (temp ushr 24)
                temp = subWord(rot) xor (RCON[i / 8] shl 24)
            } else if (i % 8 == 4) {
                temp = subWord(temp)
            }
            w[i] = w[i - 8] xor temp
        }
        return w
    }

    private fun subWord(word: Int): Int {
        return (SBOX[(word ushr 24) and 0xFF] shl 24) or
            (SBOX[(word ushr 16) and 0xFF] shl 16) or
            (SBOX[(word ushr 8) and 0xFF] shl 8) or
            SBOX[word and 0xFF]
    }

    internal fun encryptBlock(
        w: IntArray,
        input: ByteArray,
        inOffset: Int,
        output: ByteArray,
        outOffset: Int
    ) {
        var s0 = readBigEndianInt(input, inOffset) xor w[0]
        var s1 = readBigEndianInt(input, inOffset + 4) xor w[1]
        var s2 = readBigEndianInt(input, inOffset + 8) xor w[2]
        var s3 = readBigEndianInt(input, inOffset + 12) xor w[3]

        var kw = 4
        for (round in 1..13) {
            val t0 = mixColumn(
                SBOX[(s0 ushr 24) and 0xFF],
                SBOX[(s1 ushr 16) and 0xFF],
                SBOX[(s2 ushr 8) and 0xFF],
                SBOX[s3 and 0xFF]
            ) xor w[kw]

            val t1 = mixColumn(
                SBOX[(s1 ushr 24) and 0xFF],
                SBOX[(s2 ushr 16) and 0xFF],
                SBOX[(s3 ushr 8) and 0xFF],
                SBOX[s0 and 0xFF]
            ) xor w[kw + 1]

            val t2 = mixColumn(
                SBOX[(s2 ushr 24) and 0xFF],
                SBOX[(s3 ushr 16) and 0xFF],
                SBOX[(s0 ushr 8) and 0xFF],
                SBOX[s1 and 0xFF]
            ) xor w[kw + 2]

            val t3 = mixColumn(
                SBOX[(s3 ushr 24) and 0xFF],
                SBOX[(s0 ushr 16) and 0xFF],
                SBOX[(s1 ushr 8) and 0xFF],
                SBOX[s2 and 0xFF]
            ) xor w[kw + 3]

            s0 = t0
            s1 = t1
            s2 = t2
            s3 = t3
            kw += 4
        }

        // Final round (round 14, no MixColumns)
        val f0 = ((SBOX[(s0 ushr 24) and 0xFF] shl 24) or
            (SBOX[(s1 ushr 16) and 0xFF] shl 16) or
            (SBOX[(s2 ushr 8) and 0xFF] shl 8) or
            SBOX[s3 and 0xFF]) xor w[56]

        val f1 = ((SBOX[(s1 ushr 24) and 0xFF] shl 24) or
            (SBOX[(s2 ushr 16) and 0xFF] shl 16) or
            (SBOX[(s3 ushr 8) and 0xFF] shl 8) or
            SBOX[s0 and 0xFF]) xor w[57]

        val f2 = ((SBOX[(s2 ushr 24) and 0xFF] shl 24) or
            (SBOX[(s3 ushr 16) and 0xFF] shl 16) or
            (SBOX[(s0 ushr 8) and 0xFF] shl 8) or
            SBOX[s1 and 0xFF]) xor w[58]

        val f3 = ((SBOX[(s3 ushr 24) and 0xFF] shl 24) or
            (SBOX[(s0 ushr 16) and 0xFF] shl 16) or
            (SBOX[(s1 ushr 8) and 0xFF] shl 8) or
            SBOX[s2 and 0xFF]) xor w[59]

        writeBigEndianInt(f0, output, outOffset)
        writeBigEndianInt(f1, output, outOffset + 4)
        writeBigEndianInt(f2, output, outOffset + 8)
        writeBigEndianInt(f3, output, outOffset + 12)
    }

    private fun mixColumn(a0: Int, a1: Int, a2: Int, a3: Int): Int {
        val t = a0 xor a1 xor a2 xor a3
        val r0 = a0 xor t xor xtime(a0 xor a1)
        val r1 = a1 xor t xor xtime(a1 xor a2)
        val r2 = a2 xor t xor xtime(a2 xor a3)
        val r3 = a3 xor t xor xtime(a3 xor a0)
        return (r0 shl 24) or (r1 shl 16) or (r2 shl 8) or r3
    }

    private fun xtime(b: Int): Int {
        val shifted = b shl 1
        return (if ((b and 0x80) != 0) shifted xor 0x1b else shifted) and 0xFF
    }

    private fun readBigEndianInt(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)
    }

    private fun writeBigEndianInt(value: Int, out: ByteArray, offset: Int) {
        out[offset] = (value ushr 24).toByte()
        out[offset + 1] = (value ushr 16).toByte()
        out[offset + 2] = (value ushr 8).toByte()
        out[offset + 3] = value.toByte()
    }

    private fun readBigEndianLong(buf: ByteArray, offset: Int): Long {
        val hi = readBigEndianInt(buf, offset).toLong() and 0xFFFF_FFFFL
        val lo = readBigEndianInt(buf, offset + 4).toLong() and 0xFFFF_FFFFL
        return (hi shl 32) or lo
    }

    private fun writeBigEndianLong(value: Long, out: ByteArray, offset: Int) {
        writeBigEndianInt((value ushr 32).toInt(), out, offset)
        writeBigEndianInt(value.toInt(), out, offset + 4)
    }
}
