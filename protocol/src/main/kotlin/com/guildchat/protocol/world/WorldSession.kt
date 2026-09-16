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

class WorldSession(
    private val host: String,
    private val port: Int,
    private val account: String,
    private val sessionKey: ByteArray
) {
    private val connection = WorldConnection(host, port)
    private val _state = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val state: StateFlow<SessionState> = _state
    private val _characters = MutableStateFlow<List<WorldServerCharEnum.Character>>(emptyList())
    val characters: StateFlow<List<WorldServerCharEnum.Character>> = _characters
    private val _messages = MutableSharedFlow<ChatMessage>()
    val messages: SharedFlow<ChatMessage> = _messages

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clientSeed = Random.nextInt(0, Int.MAX_VALUE)

    suspend fun connect() {
        _state.value = SessionState.WorldConnect
        connection.setOnDisconnect { _ -> 
             if (_state.value !is SessionState.Error) _state.value = SessionState.Disconnected
        }
        try {
            connection.connect()
            scope.launch {
                connection.incomingPackets.collect { packet ->
                    handlePacket(packet.opcode, packet.payload)
                }
            }
        } catch (e: Exception) {
            _state.value = SessionState.Error(e.message ?: "Connect error")
        }
    }

    private suspend fun handlePacket(opcode: Int, payload: ByteArray) {
        val reader = ByteReader(ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN))
        when (opcode) {
            0x1EC -> sendAuthSession(reader.readInt())
            0x1EE -> {
                val result = reader.readByte().toInt()
                if (result == 0x0C) {
                    _state.value = SessionState.CharSelect
                    sendPacket(0x0037, ByteArray(0)) 
                } else {
                    _state.value = SessionState.Error("Auth error: $result")
                    connection.disconnect()
                }
            }
            0x003B -> _characters.value = WorldServerCharEnum(reader).characters
            0x00EE -> _state.value = SessionState.InWorld
            0x0096 -> _messages.emit(ChatServerMessage(reader).toChatMessage())
        }
    }

    private suspend fun sendAuthSession(serverSeed: Int) {
        val sha = MessageDigest.getInstance("SHA-1")
        sha.update(account.uppercase().toByteArray(Charsets.UTF_8))
        
        val pad = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        pad.putInt(0)
        pad.putInt(clientSeed)
        pad.putInt(serverSeed)
        sha.update(pad.array())
        sha.update(sessionKey)
        val digest = sha.digest()

        val authCrypt = AuthCrypt()
        authCrypt.initialize(sessionKey)
        connection.setCrypt(authCrypt)
        
        val writer = ByteWriter()
        writer.writeInt(12340)                          // Build
        writer.writeInt(0)                              // Server ID
        writer.writeString(account.uppercase())
        writer.writeByte(0)                             // Null Term
        writer.writeInt(0)                              // Unknown
        writer.writeInt(clientSeed)
        writer.writeInt(0)                              // Unknown
        writer.writeInt(0)                              // Unknown
        writer.writeInt(0)                              // Unknown
        writer.writeBytes(digest)
        writer.writeInt(0)                              // Addon Info Size

        connection.send(0x1ED, writer.toByteArray())
        
        connection.enableEncryption(true)
        connection.enableDecryption(true)
    }

    suspend fun sendPacket(opcode: Int, payload: ByteArray) = connection.send(opcode, payload)
    suspend fun selectCharacter(guid: Long) = sendPacket(0x003D, ByteWriter().apply { writeLong(guid) }.toByteArray())
    
    suspend fun sendChat(type: ChatType, message: String, target: String, channel: String) {
        val writer = ByteWriter()
        writer.writeInt(type.value)
        writer.writeInt(-1)
        if (type == ChatType.WHISPER || type == ChatType.CHANNEL) {
            writer.writeString(if (type == ChatType.CHANNEL) channel else target)
            writer.writeByte(0)
        }
        writer.writeString(message)
        writer.writeByte(0)
        sendPacket(0x0095, writer.toByteArray())
    }

    fun disconnect() {
        connection.disconnect()
        scope.cancel()
        _state.value = SessionState.Disconnected
    }
}
