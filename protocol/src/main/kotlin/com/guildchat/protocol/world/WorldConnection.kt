package com.guildchat.protocol.world

import com.guildchat.protocol.RawPacket
import com.guildchat.protocol.crypto.AuthCrypt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext

class WorldConnection(
    private val host: String,
    private val port: Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG = "WorldConnection"
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var job: Job? = null
    
    @Volatile private var crypt: AuthCrypt? = null
    @Volatile private var encryptEnabled = false
    @Volatile private var decryptEnabled = false
    private var onDisconnect: ((Exception?) -> Unit)? = null

    private val _incomingPackets = MutableSharedFlow<RawPacket>(replay = 10, extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<RawPacket> = _incomingPackets

    fun setCrypt(authCrypt: AuthCrypt?) { this.crypt = authCrypt }
    fun enableEncryption(enabled: Boolean) { this.encryptEnabled = enabled }
    fun enableDecryption(enabled: Boolean) { this.decryptEnabled = enabled }
    fun setOnDisconnect(callback: (Exception?) -> Unit) { this.onDisconnect = callback }

    suspend fun connect() = withContext(dispatcher) {
        try {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 5000)
            socket = s
            inputStream = s.getInputStream()
            outputStream = s.getOutputStream()
            job = CoroutineScope(dispatcher + SupervisorJob()).launch { readLoop() }
        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun readLoop() {
        var error: Exception? = null
        try {
            while (coroutineContext.isActive) {
                val stream = inputStream ?: break
                val header = ByteArray(4)
                
                // Read 4-byte header
                readFully(stream, header)
                val rawHex = header.joinToString("") { "%02X ".format(it) }

                if (decryptEnabled) {
                    crypt?.decrypt(header, 0, 4)
                }
                
                val decHex = header.joinToString("") { "%02X ".format(it) }
                val size = ((header[0].toInt() and 0xFF) shl 8) or (header[1].toInt() and 0xFF)
                val opcode = (header[2].toInt() and 0xFF) or ((header[3].toInt() and 0xFF) shl 8)
                
                // LOG EVERY PACKET DURING HANDSHAKE
                if (opcode in 0x1EC..0x1EE || opcode == 0x003B || opcode == 0x00EE) {
                   println("DEBUG: Handshake Opcode: 0x${opcode.toString(16).uppercase()} | Size: $size | Raw: $rawHex | Dec: $decHex")
                }

                if (size < 2 || size > 0x7FFF) {
                    throw IOException("Protocol Sync Error: Size $size is invalid. DecryptEnabled: $decryptEnabled. RawHeader: $rawHex")
                }

                val payloadSize = size - 2
                val payload = if (payloadSize > 0) {
                    val p = ByteArray(payloadSize)
                    readFully(stream, p)
                    p
                } else ByteArray(0)
                
                _incomingPackets.emit(RawPacket(opcode, payload))
            }
        } catch (e: Exception) {
            println("DEBUG: WorldConnection ReadLoop Error: ${e.message}")
            e.printStackTrace()
            error = e
        } finally {
            disconnect()
            onDisconnect?.invoke(error)
        }
    }

    private fun readFully(stream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = stream.read(buffer, offset, buffer.size - offset)
            if (read == -1) throw IOException("Server closed connection")
            offset += read
        }
    }

    suspend fun send(opcode: Int, payload: ByteArray) = withContext(dispatcher) {
        val stream = outputStream ?: return@withContext
        val size = payload.size + 4
        val header = ByteArray(6)
        header[0] = ((size shr 8) and 0xFF).toByte()
        header[1] = (size and 0xFF).toByte()
        header[2] = (opcode and 0xFF).toByte()
        header[3] = ((opcode shr 8) and 0xFF).toByte()
        header[4] = ((opcode shr 16) and 0xFF).toByte()
        header[5] = ((opcode shr 24) and 0xFF).toByte()
        
        if (encryptEnabled) crypt?.encrypt(header, 0, 6)
        
        stream.write(header)
        stream.write(payload)
        stream.flush()
    }

    fun disconnect() {
        job?.cancel()
        socket?.close()
        socket = null
        inputStream = null
        outputStream = null
    }
}
