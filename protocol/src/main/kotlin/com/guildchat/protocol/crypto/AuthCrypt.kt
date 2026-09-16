package com.guildchat.protocol.crypto

import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac

class AuthCrypt {
    private val ENCRYPT_SEED = byteArrayOf(
        0x38, 0xA7.toByte(), 0x83.toByte(), 0x15, 0xF8.toByte(), 0xEB.toByte(), 0xAD.toByte(), 0xF5.toByte(),
        0x44, 0xAE.toByte(), 0x74, 0x4C, 0x01, 0x2B, 0x5F, 0xD4.toByte(),
        0xAB.toByte(), 0xBE.toByte(), 0x84.toByte(), 0xEF.toByte(), 0x43, 0x74, 0x6D, 0xFF.toByte(),
        0x4C, 0xF9.toByte(), 0x55, 0xAD.toByte(), 0xAF.toByte(), 0x34, 0xED.toByte(), 0x46,
        0x19, 0x9A.toByte(), 0x35, 0x43, 0x11, 0x20, 0xF3.toByte(), 0xD0.toByte()
    )

    private val DECRYPT_SEED = byteArrayOf(
        0xC2.toByte(), 0xB3.toByte(), 0x72, 0x3C, 0xC6.toByte(), 0xAE.toByte(), 0xD9.toByte(), 0xB5.toByte(),
        0x34, 0x3C, 0xFA.toByte(), 0x92.toByte(), 0x82.toByte(), 0x8A.toByte(), 0xED.toByte(), 0x46,
        0x21, 0x0A, 0x58.toByte(), 0xBB.toByte(), 0x07, 0x4E, 0x20, 0xBE.toByte(), 0x39,
        0x39, 0xA9.toByte(), 0xFE.toByte(), 0x7C, 0x2B, 0x55, 0xAF.toByte(), 0x2B, 0x29,
        0x8E.toByte(), 0x92.toByte(), 0xBC.toByte(), 0x51, 0x5E, 0x8F.toByte()
    )

    private inner class RC4(key: ByteArray) {
        private val s = IntArray(256) { it }
        private var x = 0
        private var y = 0

        init {
            var j = 0
            for (i in 0..255) {
                j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) % 256
                val temp = s[i]
                s[i] = s[j]
                s[j] = temp
            }
            repeat(1024) { step() }
        }

        private fun step(): Int {
            x = (x + 1) % 256
            y = (y + s[x]) % 256
            val temp = s[x]
            s[x] = s[y]
            s[y] = temp
            return s[(s[x] + s[y]) % 256]
        }

        fun apply(data: ByteArray, offset: Int, length: Int) {
            for (i in 0 until length) {
                data[offset + i] = (data[offset + i].toInt() xor step()).toByte()
            }
        }
    }

    private var decryptRC4: RC4? = null
    private var encryptRC4: RC4? = null

    fun initialize(sessionKey: ByteArray) {
        // BIT-EXACT FIX: Seed is the Key, sessionKey is the Data
        encryptRC4 = RC4(calculateHmac(ENCRYPT_SEED, sessionKey))
        decryptRC4 = RC4(calculateHmac(DECRYPT_SEED, sessionKey))
    }

    private fun calculateHmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data)
    }

    fun decrypt(data: ByteArray, offset: Int, length: Int) = decryptRC4?.apply(data, offset, length)
    fun encrypt(data: ByteArray, offset: Int, length: Int) = encryptRC4?.apply(data, offset, length)
}
