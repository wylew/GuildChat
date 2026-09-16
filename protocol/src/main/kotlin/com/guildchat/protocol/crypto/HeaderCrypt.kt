package com.guildchat.protocol.crypto

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * World packet header encryption (Arc4).
 * Reference: tuicraft/src/crypto and gtker/wow_messages
 */
class HeaderCrypt(sessionKey: ByteArray) {
    companion object {
        private val ENCRYPT_KEY = byteArrayOf(
            0xC2.toByte(), 0xB3.toByte(), 0x72.toByte(), 0x3C.toByte(), 0xC6.toByte(), 0xAE.toByte(), 0xD9.toByte(), 0xB5.toByte(),
            0x34.toByte(), 0x3C.toByte(), 0x53.toByte(), 0xEE.toByte(), 0x2F.toByte(), 0x43.toByte(), 0xB7.toByte(), 0x3C.toByte(),
            0x30.toByte(), 0x73.toByte(), 0x95.toByte(), 0x49.toByte()
        )
        private val DECRYPT_KEY = byteArrayOf(
            0xCC.toByte(), 0x98.toByte(), 0xAE.toByte(), 0x04.toByte(), 0xE8.toByte(), 0x97.toByte(), 0xE4.toByte(), 0x2C.toByte(),
            0x20.toByte(), 0xDA.toByte(), 0x2B.toByte(), 0x35.toByte(), 0x2F.toByte(), 0x06.toByte(), 0x68.toByte(), 0x3F.toByte(),
            0xB2.toByte(), 0xEE.toByte(), 0xC4.toByte(), 0xD2.toByte()
        )
    }

    private val encryptCipher: Cipher
    private val decryptCipher: Cipher

    init {
        val encKey = hmacSha1(ENCRYPT_KEY, sessionKey)
        val decKey = hmacSha1(DECRYPT_KEY, sessionKey)

        encryptCipher = Cipher.getInstance("RC4")
        encryptCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "RC4"))

        decryptCipher = Cipher.getInstance("RC4")
        decryptCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(decKey, "RC4"))

        // WoW skips the first 1024 bytes of the RC4 stream
        val skip = ByteArray(1024)
        encryptCipher.update(skip)
        decryptCipher.update(skip)
    }

    fun encrypt(data: ByteArray, offset: Int, length: Int) {
        val result = encryptCipher.update(data, offset, length)
        System.arraycopy(result, 0, data, offset, length)
    }

    fun decrypt(data: ByteArray, offset: Int, length: Int) {
        val result = decryptCipher.update(data, offset, length)
        System.arraycopy(result, 0, data, offset, length)
    }

    private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        return mac.doFinal(data)
    }
}
