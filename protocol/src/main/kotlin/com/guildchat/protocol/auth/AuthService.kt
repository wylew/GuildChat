package com.guildchat.protocol.auth

import com.guildchat.protocol.ByteReader
import com.guildchat.protocol.ByteWriter
import com.guildchat.protocol.Connection
import com.guildchat.protocol.crypto.Srp6Client
import com.guildchat.protocol.toLittleEndianByteArray
import java.nio.ByteBuffer

/**
 * Service to handle the SRP-6 authentication flow against the WoW 3.3.5a auth server.
 * Deliverable: Returns the session key K and the server proof response.
 */
class AuthService(private val connection: Connection) {

    data class AuthResult(
        val sessionKey: ByteArray,
        val proofResponse: AuthServerProof
    )

    /**
     * Executes the SRP-6 login flow.
     * @param username The account name.
     * @param password The account password.
     * @return AuthResult containing the 40-byte session key K and server proof.
     */
    suspend fun login(username: String, password: String): AuthResult {
        // 1. Send CMSG_AUTH_LOGON_CHALLENGE (Opcode 0x00)
        val challenge = AuthClientChallenge(username)
        val challengeWriter = ByteWriter()
        challenge.write(challengeWriter)
        connection.send(challengeWriter.toByteArray())

        // 2. Receive SMSG_AUTH_LOGON_CHALLENGE (Opcode, Unused, Result)
        val opcode = connection.readByte()
        val unused = connection.readByte()
        val result = connection.readByte()
        
        if (result != 0) {
            throw Exception("Auth Logon Challenge failed. Protocol Result: $result")
        }
        
        // Read SUCCESS payload (116 bytes for 3.3.5a)
        val challengePayload = connection.readExactly(116)
        val fullChallenge = ByteArray(119)
        fullChallenge[0] = opcode.toByte()
        fullChallenge[1] = unused.toByte()
        fullChallenge[2] = result.toByte()
        System.arraycopy(challengePayload, 0, fullChallenge, 3, 116)
        
        val serverChallenge = AuthServerChallenge(ByteReader(ByteBuffer.wrap(fullChallenge)))

        // 3. Perform SRP-6 calculations
        val srpClient = Srp6Client(username, password)
        val k = srpClient.calculateSessionKey(serverChallenge.salt, serverChallenge.serverPublicKey)

        // 4. Send CMSG_AUTH_LOGON_PROOF (Opcode 0x01)
        val proof = AuthClientProof(
            A = srpClient.A.toLittleEndianByteArray(32),
            M1 = srpClient.clientProof
        )
        val proofWriter = ByteWriter()
        proof.write(proofWriter)
        connection.send(proofWriter.toByteArray())

        // 5. Receive SMSG_AUTH_LOGON_PROOF (Opcode 0x01)
        val proofOpcode = connection.readByte()
        val proofResult = connection.readByte()

        if (proofResult != 0) {
            throw Exception("Auth Logon Proof failed. Error code: $proofResult")
        }

        // Read SUCCESS payload (30 bytes)
        val proofPayload = connection.readExactly(30)
        val fullProof = ByteArray(32)
        fullProof[0] = proofOpcode.toByte()
        fullProof[1] = proofResult.toByte()
        System.arraycopy(proofPayload, 0, fullProof, 2, 30)
        
        val serverProof = AuthServerProof(ByteReader(ByteBuffer.wrap(fullProof)))

        return AuthResult(
            sessionKey = k,
            proofResponse = serverProof
        )
    }
}
