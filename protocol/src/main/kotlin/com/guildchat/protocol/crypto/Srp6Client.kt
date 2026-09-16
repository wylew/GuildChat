package com.guildchat.protocol.crypto

import com.guildchat.protocol.toLittleEndianByteArray
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SRP-6 implementation for WoW 3.3.5a authentication.
 */
class Srp6Client(
    private val username: String,
    private val password: String
) {
    companion object {
        // Standard WoW SRP-6 constants
        val N = BigInteger("894B645E89E1535BBDAD5B8B290650530801B18EBFBF5E8FAB3C82872A3E9BB7", 16)
        val g = BigInteger.valueOf(7)
        val k = BigInteger.valueOf(3)
    }

    private val random = SecureRandom()
    private val a = BigInteger(152, random) // 19-byte ephemeral private key
    val A = g.modPow(a, N) // Public ephemeral key A = g^a % N

    lateinit var sessionKey: ByteArray
        private set
    lateinit var clientProof: ByteArray
        private set

    /**
     * Calculates the 40-byte session key K and the client proof M1.
     * @param salt The 32-byte salt from the server challenge.
     * @param B_bytes The 32-byte server public key B (Little-Endian).
     */
    fun calculateSessionKey(salt: ByteArray, B_bytes: ByteArray): ByteArray {
        // Interpret B as Little-Endian for modular arithmetic
        val B = BigInteger(1, B_bytes.reversedArray())
        
        val sha = MessageDigest.getInstance("SHA-1")
        
        // 1. x = H(s, H(U, ":", P))
        sha.update(username.uppercase().toByteArray())
        sha.update(":".toByteArray())
        sha.update(password.uppercase().toByteArray())
        val userHash = sha.digest()
        
        sha.reset()
        sha.update(salt)
        sha.update(userHash)
        // WoW specific: Interpret digest as Little-Endian for x
        val x = BigInteger(1, sha.digest().reversedArray())
        
        // 2. u = H(A, B) where A and B are their 32-byte LE packet representations
        sha.reset()
        val A_bytes = A.toLittleEndianByteArray(32)
        sha.update(A_bytes)
        sha.update(B_bytes)
        val u = BigInteger(1, sha.digest().reversedArray())
        
        // 3. S = (B - k*g^x) ^ (a + u*x) % N
        val v = g.modPow(x, N)
        val term1 = B.subtract(k.multiply(v).mod(N)).mod(N)
        val term2 = a.add(u.multiply(x))
        val S = term1.modPow(term2, N)
        
        // 4. K = Interleave(S)
        // Derives 40-byte K from 32-byte shared secret S
        val S_bytes = S.toLittleEndianByteArray(32)
        sessionKey = calculateInterleavedHash(S_bytes)
        
        // 5. M1 = H(H(N) ^ H(g), H(U), s, A, B, K)
        clientProof = calculateM1(salt, A_bytes, B_bytes, sessionKey)
        
        return sessionKey
    }

    private fun calculateM1(salt: ByteArray, A_bytes: ByteArray, B_bytes: ByteArray, K: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-1")
        
        // H(N) and H(g) using Little-Endian representation of constants
        val hN = sha.digest(N.toLittleEndianByteArray(32))
        val hg = sha.digest(g.toLittleEndianByteArray(1))
        val hNg = ByteArray(20) { i -> (hN[i].toInt() xor hg[i].toInt()).toByte() }
        
        val hU = sha.digest(username.uppercase().toByteArray())
        
        sha.reset()
        sha.update(hNg)
        sha.update(hU)
        sha.update(salt)
        sha.update(A_bytes)
        sha.update(B_bytes)
        sha.update(K)
        return sha.digest()
    }

    private fun calculateInterleavedHash(S: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-1")
        val sEven = ByteArray(16) { i -> S[i * 2] }
        val sOdd = ByteArray(16) { i -> S[i * 2 + 1] }
        
        val hEven = sha.digest(sEven)
        val hOdd = sha.digest(sOdd)
        
        val result = ByteArray(40)
        for (i in 0 until 20) {
            result[i * 2] = hEven[i]
            result[i * 2 + 1] = hOdd[i]
        }
        return result
    }

    fun verifyServerProof(M2: ByteArray): Boolean {
        val sha = MessageDigest.getInstance("SHA-1")
        sha.update(A.toLittleEndianByteArray(32))
        sha.update(clientProof)
        sha.update(sessionKey)
        return sha.digest().contentEquals(M2)
    }
}
