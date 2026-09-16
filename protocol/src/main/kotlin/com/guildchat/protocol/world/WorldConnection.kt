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

/**
 * Simple internal logger to replace android.util.Log in this pure JVM module.
 */
private object Log {
    fun d(tag: String, msg: String) = println("D/$tag: $msg")
    fun i(tag: String, msg: String) = println("I/$tag: $msg")
    fun v(tag: String, msg: String) = println("V/$tag: $msg")
    fun e(tag: String, msg: String, t: Throwable? = null) {
        System.err.println("E/$tag: $msg")
        t?.printStackTrace()
    }
}

/**
 * Manages the encrypted/decrypted World connection.
 */
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
    
    @Volatile
    private var crypt: AuthCrypt? = null

    @Volatile
    private var encryptEnabled = false

    @Volatile
    private var decryptEnabled = false

    private var onDisconnect: ((Exception?) -> Unit)? = null

    private val _incomingPackets = MutableSharedFlow<RawPacket>(replay = 10, extraBufferCapacity = 64)
    val incomingPackets: SharedFlow<RawPacket> = _incomingPackets

    fun setCrypt(authCrypt: AuthCrypt?) {
        this.crypt = authCrypt
    }

    /**
     * Toggles outgoing packet header encryption.
     */
    fun enableEncryption(enabled: Boolean) {
        Log.d(TAG, "Outgoing encryption enabled: $enabled")
        this.encryptEnabled = enabled
    }

    /**
     * Toggles incoming packet header decryption.
     */
    fun enableDecryption(enabled: Boolean) {
        Log.d(TAG, "Incoming decryption enabled: $enabled")
        this.decryptEnabled = enabled
    }

    fun setOnDisconnect(callback: (Exception?) -> Unit) {
        this.onDisconnect = callback
    }

    suspend fun connect() = withContext(dispatcher) {
        try {
            Log.d(TAG, "Connecting to $host:$port")
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 5000)
            socket = s
            inputStream = s.getInputStream()
            outputStream = s.getOutputStream()
            Log.i(TAG, "Connected to $host:$port")

            job = CoroutineScope(dispatcher + SupervisorJob()).launch {
                readLoop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun readLoop() {
        var error: Exception? = null
        try {
            while (coroutineContext.isActive) {
                val stream = inputStream ?: break
                
                // 1. Read Header (S->C: 4 bytes)
                val header = ByteArray(4)
                readFully(stream, header)
                
                // Debug raw header bytes before decryption (Milestone 10)
                val rawHex = header.joinToString("") { "%02X".format(it) }
                Log.d(TAG, "PRE-DECRYPT Header: $rawHex")

                // Decrypt if encryption is activated for incoming
                if (decryptEnabled) {
                    crypt?.decrypt(header, 0, 4)
                }
                
                // 3.3.5a S->C Header: [Size: 2 bytes, Big Endian] [Opcode: 2 bytes, Little Endian]
                val size = ((header[0].toInt() and 0xFF) shl 8) or (header[1].toInt() and 0xFF)
                val opcode = (header[2].toInt() and 0xFF) or ((header[3].toInt() and 0xFF) shl 8)
                
                // Log non-keepalive packets for debugging
                if (opcode != 0x01DD && opcode != 0x0390) {
                    Log.d(TAG, "Received Opcode 0x${opcode.toString(16).uppercase()}, size $size, DecryptEnabled: $decryptEnabled")
                }

                // Payload size is total size minus opcode size (2 bytes)
                val payloadSize = size - 2
                if (payloadSize < 0) {
                    throw IOException("Invalid payload size: $payloadSize (total size $size)")
                }

                val payload = if (payloadSize > 0) {
                    val p = ByteArray(payloadSize)
                    readFully(stream, p)
                    p
                } else {
                    ByteArray(0)
                }
                
                _incomingPackets.emit(RawPacket(opcode, payload))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Read loop error: ${e.message}", e)
            error = e
        } finally {
            Log.d(TAG, "Connection closed")
            disconnect()
            onDisconnect?.invoke(error)
        }
    }

    @Throws(IOException::class)
    private fun readFully(stream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = stream.read(buffer, offset, buffer.size - offset)
            if (read == -1) throw IOException("Socket closed unexpectedly")
            offset += read
        }
    }

    suspend fun send(opcode: Int, payload: ByteArray) = withContext(dispatcher) {
        val stream = outputStream ?: return@withContext
        
        if (opcode != 0x01DC && opcode != 0x0391) {
            Log.d(TAG, "Sending Opcode 0x${opcode.toString(16).uppercase()}, size ${payload.size}, EncryptEnabled: $encryptEnabled")
        }

        // 2. Build Header (C->S: 6 bytes)
        // Header: 2 bytes size (Big Endian) + 4 bytes opcode (Little Endian)
        val size = payload.size + 4
        
        val header = ByteArray(6)
        // Size: 2 bytes Big Endian
        header[0] = ((size shr 8) and 0xFF).toByte()
        header[1] = (size and 0xFF).toByte()
        
        // Opcode: 4 bytes Little Endian
        header[2] = (opcode and 0xFF).toByte()
        header[3] = ((opcode shr 8) and 0xFF).toByte()
        header[4] = ((opcode shr 16) and 0xFF).toByte()
        header[5] = ((opcode shr 24) and 0xFF).toByte()
        
        // Encrypt if encryption is activated for outgoing
        if (encryptEnabled) {
            crypt?.encrypt(header, 0, 6)
        }
        
        try {
            stream.write(header)
            stream.write(payload)
            stream.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send packet 0x${opcode.toString(16).uppercase()}: ${e.message}", e)
            throw e
        }
    }

    fun disconnect() {
        job?.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
        socket = null
        inputStream = null
        outputStream = null
        crypt = null
        encryptEnabled = false
        decryptEnabled = false
    }
}
