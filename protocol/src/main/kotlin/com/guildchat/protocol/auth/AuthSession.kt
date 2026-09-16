package com.guildchat.protocol.auth

import com.guildchat.protocol.ByteReader
import com.guildchat.protocol.ByteWriter
import com.guildchat.protocol.Connection
import com.guildchat.protocol.SessionState
import com.guildchat.protocol.crypto.Srp6Client
import com.guildchat.protocol.toLittleEndianByteArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer

/**
 * Manages the authentication lifecycle from SRP-6 Logon to Realm List retrieval.
 * Reference: tuicraft/src/auth and gtker/wow_messages
 */
class AuthSession(
    private val host: String,
    private val port: Int = 3724
) {
    private val connection = Connection(host, port)
    private val _state = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val state: StateFlow<SessionState> = _state

    var sessionKey: ByteArray? = null
        private set

    /**
     * Executes the full login flow and returns the list of available realms.
     */
    suspend fun authenticate(username: String, password: String): AuthServerRealmList? {
        try {
            _state.value = SessionState.AuthChallenge
            connection.connect()

            // 1. Send CMSG_AUTH_LOGON_CHALLENGE
            val challenge = AuthClientChallenge(username)
            val writer = ByteWriter()
            challenge.write(writer)
            connection.send(writer.toByteArray())

            // 2. Receive SMSG_AUTH_LOGON_CHALLENGE (Opcode, Unused, Result)
            val opcode = connection.readByte()
            val unused = connection.readByte()
            val result = connection.readByte()
            
            if (result != 0) {
                _state.value = SessionState.Error("Auth challenge failed with result: $result")
                return null
            }
            
            // Read remaining SUCCESS payload (116 bytes for 3.3.5a)
            val challengePayload = connection.readExactly(116)
            val fullChallenge = ByteArray(119)
            fullChallenge[0] = opcode.toByte()
            fullChallenge[1] = unused.toByte()
            fullChallenge[2] = result.toByte()
            System.arraycopy(challengePayload, 0, fullChallenge, 3, 116)
            
            val serverChallenge = AuthServerChallenge(ByteReader(ByteBuffer.wrap(fullChallenge)))

            _state.value = SessionState.AuthProof
            
            // 3. Calculate SRP-6 Proof
            val srpClient = Srp6Client(username, password)
            val key = srpClient.calculateSessionKey(serverChallenge.salt, serverChallenge.serverPublicKey)
            sessionKey = key

            // 4. Send CMSG_AUTH_LOGON_PROOF
            val proof = AuthClientProof(
                A = srpClient.A.toLittleEndianByteArray(32),
                M1 = srpClient.clientProof
            )
            val proofWriter = ByteWriter()
            proof.write(proofWriter)
            connection.send(proofWriter.toByteArray())

            // 5. Receive SMSG_AUTH_LOGON_PROOF
            val proofOpcode = connection.readByte()
            val proofResult = connection.readByte()

            if (proofResult != 0) {
                _state.value = SessionState.Error("Auth proof failed with error: $proofResult")
                return null
            }

            val proofPayload = connection.readExactly(30)
            val fullProof = ByteArray(32)
            fullProof[0] = proofOpcode.toByte()
            fullProof[1] = proofResult.toByte()
            System.arraycopy(proofPayload, 0, fullProof, 2, 30)
            
            _state.value = SessionState.RealmList
            
            // 6. Request Realm List (CMSG_REALM_LIST)
            val realmListReq = AuthClientRealmList()
            val realmWriter = ByteWriter()
            realmListReq.write(realmWriter)
            connection.send(realmWriter.toByteArray())

            // 7. Parse SMSG_REALM_LIST
            val realmPacketData = connection.readRealmListPacket()
            return AuthServerRealmList(ByteReader(ByteBuffer.wrap(realmPacketData)))
            
        } catch (e: Exception) {
            // Use toString() to ensure we get the exception class name if message is null
            _state.value = SessionState.Error(e.message ?: e.toString())
            return null
        }
    }

    fun disconnect() {
        connection.disconnect()
        _state.value = SessionState.Disconnected
    }
}
