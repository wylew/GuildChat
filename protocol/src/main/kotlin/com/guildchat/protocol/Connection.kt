package com.guildchat.protocol

import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manages the TCP connection for the Auth server.
 * Provides synchronous-style reading within a coroutine context.
 */
class Connection(
    private val host: String,
    private val port: Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    suspend fun connect() = withContext(dispatcher) {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 5000)
        socket = s
        inputStream = s.getInputStream()
        outputStream = s.getOutputStream()
    }

    suspend fun send(data: ByteArray) = withContext(dispatcher) {
        val stream = outputStream ?: throw Exception("Not connected")
        stream.write(data)
        stream.flush()
    }

    /**
     * Reads exactly [length] bytes from the socket.
     */
    suspend fun readExactly(length: Int): ByteArray = withContext(dispatcher) {
        val buffer = ByteArray(length)
        var offset = 0
        val stream = inputStream ?: throw Exception("Not connected")
        while (offset < length) {
            val read = withContext(Dispatchers.IO) {
                stream.read(buffer, offset, length - offset)
            }
            if (read == -1) throw Exception("Socket closed by server")
            offset += read
        }
        buffer
    }

    /**
     * Reads a single byte (opcode or result).
     */
    suspend fun readByte(): Int = withContext(dispatcher) {
        val stream = inputStream ?: throw Exception("Not connected")
        val b = stream.read()
        if (b == -1) throw Exception("Socket closed")
        b
    }

    /**
     * Specialized read for the Realm List packet which contains a size header.
     * SMSG_REALM_LIST: [u8] opcode (0x10), [u16] size, [u32] reserved...
     */
    suspend fun readRealmListPacket(): ByteArray {
        val opcode = readByte()
        if (opcode != 0x10) throw Exception("Expected Realm List opcode (0x10), got $opcode")
        
        val sizeBytes = readExactly(2)
        val size = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        
        val payload = readExactly(size)
        val full = ByteArray(3 + size)
        full[0] = 0x10.toByte()
        System.arraycopy(sizeBytes, 0, full, 1, 2)
        System.arraycopy(payload, 0, full, 3, size)
        return full
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignore
        }
        socket = null
        inputStream = null
        outputStream = null
    }
}
