package com.guildchat.protocol.world

import com.guildchat.protocol.ByteReader
import com.guildchat.protocol.ByteWriter
import com.guildchat.protocol.SessionState
import com.guildchat.protocol.crypto.AuthCrypt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Manages the World Server session lifecycle and heartbeats.
 * Cited Reference: tuicraft/src/world/session.ts and gtker/wow_messages
 */
class WorldSession(
    private val host: String,
    private val port: Int,
    private val account: String,
    private val sessionKey: ByteArray
) {
    private val connection = WorldConnection(host, port)
    private val _state = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val state: StateFlow<SessionState> = _state

    private val _messages = MutableSharedFlow<ChatMessage>()
    val messages: SharedFlow<ChatMessage> = _messages

    private val _characters = MutableStateFlow<List<WorldServerCharEnum.Character>>(emptyList())
    val characters: StateFlow<List<WorldServerCharEnum.Character>> = _characters

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var keepAliveJob: Job? = null
    private val clientSeed = Random.nextInt()
    
    // Monotonic clock for time synchronization
    private val sessionStartTime = System.nanoTime()

    private fun getSessionTicks(): Int {
        return ((System.nanoTime() - sessionStartTime) / 1_000_000).toInt()
    }

    suspend fun connect() {
        _state.value = SessionState.WorldConnect
        try {
            connection.connect()
            
            scope.launch {
                connection.incomingPackets.collect { packet ->
                    handlePacket(packet.opcode, packet.payload)
                }
            }
        } catch (e: Exception) {
            _state.value = SessionState.Error("Failed to connect: ${e.message}")
        }
    }

    private suspend fun handlePacket(opcode: Int, payload: ByteArray) {
        val reader = ByteReader(ByteBuffer.wrap(payload))
        when (opcode) {
            WorldOpcode.SMSG_AUTH_CHALLENGE -> {
                val challenge = WorldServerAuthChallenge(reader)
                sendAuthSession(challenge.serverSeed)
            }
            WorldOpcode.SMSG_AUTH_RESPONSE -> {
                val response = WorldServerAuthResponse(reader)
                if (response.result == 0x0C) { // AUTH_OK
                    _state.value = SessionState.CharSelect
                    startKeepAlive()
                    sendPacket(WorldClientCharEnum())
                } else {
                    _state.value = SessionState.Error("World Auth failed: ${response.result}")
                }
            }
            WorldOpcode.SMSG_CHAR_ENUM -> {
                val charEnum = WorldServerCharEnum(reader)
                _characters.value = charEnum.characters
            }
            WorldOpcode.SMSG_LOGIN_VERIFY_WORLD -> {
                _state.value = SessionState.InWorld
            }
            WorldOpcode.SMSG_TIME_SYNC_REQ -> {
                val req = WorldServerTimeSyncRequest(reader)
                sendPacket(WorldClientTimeSyncResponse(req.counter, getSessionTicks()))
            }
            WorldOpcode.SMSG_PONG -> {
                WorldServerPong(reader)
            }
            WorldOpcode.SMSG_MESSAGECHAT -> {
                val msg = ChatServerMessage(reader).toChatMessage()
                _messages.emit(msg)
            }
        }
    }

    private suspend fun sendAuthSession(serverSeed: Int) {
        val sha = MessageDigest.getInstance("SHA-1")
        sha.update(account.uppercase().toByteArray())
        
        // Data to hash: AccountName (Upper) + 0000 (4 null bytes) + ClientSeed (4 bytes) + ServerSeed + SessionKey
        val buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0) // 4 null bytes
        buf.putInt(clientSeed)
        buf.putInt(serverSeed)
        sha.update(buf.array())
        sha.update(sessionKey)
        val digest = sha.digest()

        val authPacket = WorldClientAuthSession(
            build = 12340,
            serverId = 0,
            account = account.uppercase(),
            clientSeed = clientSeed,
            digest = digest
        )
        
        // WotLK: CMSG_AUTH_SESSION must be sent unencrypted
        connection.setCrypt(AuthCrypt(sessionKey))
        sendPacket(authPacket)
        
        // Transition Timing Fix: 
        // Enable encryption (outgoing) and decryption (incoming) immediately after sending CMSG_AUTH_SESSION.
        // SMSG_AUTH_RESPONSE header is expected to be encrypted in AzerothCore 3.3.5a.
        connection.enableEncryption(true)
        connection.enableDecryption(true)
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            var sequence = 0
            while (isActive) {
                delay(30000)
                sendPacket(WorldClientPing(sequence++))
            }
        }
    }

    suspend fun sendPacket(packet: com.guildchat.protocol.Packet) {
        val writer = ByteWriter()
        packet.write(writer)
        connection.send(packet.opcode, writer.toByteArray())
    }

    suspend fun selectCharacter(guid: Long) {
        sendPacket(WorldClientPlayerLogin(guid))
    }

    suspend fun selectRealm(realm: com.guildchat.protocol.auth.Realm) {
        // Handled via connect() in this class
    }

    suspend fun sendChat(type: ChatType, message: String, target: String = "", channel: String = "") {
        sendPacket(ChatClientMessage(type = type, message = message, target = target, channel = channel))
    }

    fun disconnect() {
        keepAliveJob?.cancel()
        scope.cancel()
        connection.disconnect()
        _state.value = SessionState.Disconnected
    }
}
