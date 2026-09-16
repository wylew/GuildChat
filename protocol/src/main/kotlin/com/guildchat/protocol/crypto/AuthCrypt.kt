package com.guildchat.protocol.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * ARC4 (RC4) implementation for WoW packet header encryption.
 */
class Arc4(key: ByteArray) {
    private val state = IntArray(256) { it }
    private var x = 0
    private var y = 0

    init {
        var j = 0
        for (i in 0..255) {
            j = (j + state[i] + (key[i % key.size].toInt() and 0xFF)) % 256
            val temp = state[i]
            state[i] = state[j]
            state[j] = temp
        }
    }

    /**
     * Processes (encrypts or decrypts) data in place.
     */
    fun process(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        for (n in 0 until length) {
            x = (x + 1) % 256
            y = (y + state[x]) % 256
            val temp = state[x]
            state[x] = state[y]
            state[y] = temp
            val k = state[(state[x] + state[y]) % 256]
            data[offset + n] = (data[offset + n].toInt() xor k).toByte()
        }
    }
}

/**
 * Manages the encryption and decryption of WoW World packet headers.
 * Reference: tuicraft/src/world/crypt.ts and TrinityCore AuthCrypt.cpp
 */
class AuthCrypt(sessionKey: ByteArray) {
    companion object {
        // Seeds for HMAC-SHA1 initialization per WotLK 3.3.5a
        // Client-to-Server (Outgoing) seed
        private val ENCRYPT_SEED = byteArrayOf(
            0xC2.toByte(), 0xB3.toByte(), 0x72.toByte(), 0x3C.toByte(), 0xC6.toByte(), 0xAE.toByte(), 0xD9.toByte(), 0xB5.toByte(),
            0x34.toByte(), 0x3C.toByte(), 0x53.toByte(), 0xEE.toByte(), 0x2F.toByte(), 0x43.toByte(), 0xB7.toByte(), 0x3C.toByte(),
            0x30.toByte(), 0x73.toByte(), 0x95.toByte(), 0x49.toByte()
        )
        // Server-to-Client (Incoming) seed
        private val DECRYPT_SEED = byteArrayOf(
            0xCC.toByte(), 0x98.toByte(), 0xAE.toByte(), 0x04.toByte(), 0xE8.toByte(), 0x97.toByte(), 0xE4.toByte(), 0x2C.toByte(),
            0x20.toByte(), 0xDA.toByte(), 0x2B.toByte(), 0x35.toByte(), 0x2F.toByte(), 0x06.toByte(), 0x68.toByte(), 0x3F.toByte(),
            0xB2.toByte(), 0xEE.toByte(), 0xC4.toByte(), 0xD2.toByte()
        )
    }

    private val encryptArc4: Arc4
    private val decryptArc4: Arc4

    init {
        // WotLK 3.3.5a: HMAC-SHA1(key=seed, data=sessionKey)
        val encryptKey = hmacSha1(ENCRYPT_SEED, sessionKey)
        val decryptKey = hmacSha1(DECRYPT_SEED, sessionKey)

        encryptArc4 = Arc4(encryptKey)
        decryptArc4 = Arc4(decryptKey)

        // Drop the first 1024 bytes of the RC4 keystream
        // Mitigation for RC4 vulnerabilities as implemented in AzerothCore/tuicraft
        val dummy = ByteArray(1024)
        encryptArc4.process(dummy)
        decryptArc4.process(dummy)
    }

    /**
     * Encrypts an outgoing packet header (6 bytes for Client-to-Server).
     */
    fun encrypt(data: ByteArray, offset: Int = 0, length: Int = 6) {
        encryptArc4.process(data, offset, length)
    }

    /**
     * Decrypts an incoming packet header (4 bytes for Server-to-Client).
     */
    fun decrypt(data: ByteArray, offset: Int = 0, length: Int = 4) {
        decryptArc4.process(data, offset, length)
    }

    private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data)
    }
}
