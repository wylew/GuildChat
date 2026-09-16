package com.guildchat.protocol.auth

import com.guildchat.protocol.ByteWriter
import com.guildchat.protocol.Packet

/**
 * Packet layout per gtker/wow_messages CMSG_AUTH_LOGON_PROOF
 */
class AuthClientProof(
    val A: ByteArray,
    val M1: ByteArray,
    val crc: ByteArray = ByteArray(20)
) : Packet {
    override val opcode: Int = AuthOpcode.LOGIN_PROOF

    override fun write(writer: ByteWriter) {
        writer.writeByte(opcode)
        writer.writeBytes(A)
        writer.writeBytes(M1)
        writer.writeBytes(crc)
        writer.writeByte(0) // number of keys
        writer.writeByte(0) // security flags
    }
}
