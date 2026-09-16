package com.guildchat.protocol

interface Packet {
    val opcode: Int
    fun write(writer: ByteWriter)
}

/**
 * Raw representation of a packet for logging and initial framing.
 */
data class RawPacket(val opcode: Int, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RawPacket
        if (opcode != other.opcode) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = opcode
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
