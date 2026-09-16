package com.guildchat.protocol

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ByteWriter(capacity: Int = 1024) {
    private var buffer = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN)

    fun setOrder(order: ByteOrder) {
        buffer.order(order)
    }

    fun writeByte(value: Int) {
        ensureCapacity(1)
        buffer.put(value.toByte())
    }

    fun writeShort(value: Int) {
        ensureCapacity(2)
        buffer.putShort(value.toShort())
    }

    fun writeInt(value: Int) {
        ensureCapacity(4)
        buffer.putInt(value)
    }

    fun writeLong(value: Long) {
        ensureCapacity(8)
        buffer.putLong(value)
    }

    fun writeBytes(bytes: ByteArray) {
        ensureCapacity(bytes.size)
        buffer.put(bytes)
    }

    fun writeString(value: String, nullTerminated: Boolean = true) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeBytes(bytes)
        if (nullTerminated) {
            writeByte(0)
        }
    }

    private fun ensureCapacity(extra: Int) {
        if (buffer.remaining() < extra) {
            val newCapacity = (buffer.capacity() + extra) * 2
            val newBuffer = ByteBuffer.allocate(newCapacity).order(buffer.order())
            buffer.flip()
            newBuffer.put(buffer)
            buffer = newBuffer
        }
    }

    fun toByteArray(): ByteArray {
        val currentPos = buffer.position()
        val bytes = ByteArray(currentPos)
        buffer.flip()
        buffer.get(bytes)
        buffer.position(currentPos)
        buffer.limit(buffer.capacity())
        return bytes
    }
}

class ByteReader(private val buffer: ByteBuffer) {
    init {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
    }

    fun setOrder(order: ByteOrder) {
        buffer.order(order)
    }

    fun readByte(): Int = buffer.get().toInt() and 0xFF
    fun readShort(): Int = buffer.short.toInt() and 0xFFFF
    fun readInt(): Int = buffer.int
    fun readLong(): Long = buffer.long

    fun readBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return bytes
    }

    fun readRemaining(): ByteArray = readBytes(buffer.remaining())

    /**
     * Reads a null-terminated UTF-8 string.
     */
    fun readString(): String {
        val start = buffer.position()
        var length = 0
        while (buffer.hasRemaining() && buffer.get() != 0.toByte()) {
            length++
        }
        val bytes = ByteArray(length)
        val end = buffer.position()
        buffer.position(start)
        buffer.get(bytes)
        if (buffer.hasRemaining()) {
            buffer.get() // consume the null terminator
        }
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Reads a UTF-8 string of fixed length.
     */
    fun readFixedString(length: Int): String {
        val bytes = readBytes(length)
        // Trim null characters if any
        val nullIndex = bytes.indexOf(0.toByte())
        val actualLength = if (nullIndex != -1) nullIndex else length
        return String(bytes, 0, actualLength, Charsets.UTF_8)
    }

    fun hasRemaining(): Boolean = buffer.hasRemaining()
    fun remaining(): Int = buffer.remaining()
}

fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }

fun BigInteger.toLittleEndianByteArray(size: Int): ByteArray {
    val bytes = this.toByteArray()
    val result = ByteArray(size)
    val start = if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) 1 else 0
    val len = minOf(size, bytes.size - start)
    for (i in 0 until len) {
        result[i] = bytes[bytes.size - 1 - i]
    }
    return result
}

fun BigInteger.toBigEndianByteArray(size: Int): ByteArray {
    val bytes = this.toByteArray()
    val result = ByteArray(size)
    val start = if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) 1 else 0
    val len = minOf(size, bytes.size - start)
    System.arraycopy(bytes, bytes.size - len, result, size - len, len)
    return result
}
