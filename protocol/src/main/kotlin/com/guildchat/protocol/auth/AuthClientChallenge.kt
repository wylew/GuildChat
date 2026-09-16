package com.guildchat.protocol.auth

import com.guildchat.protocol.ByteWriter
import com.guildchat.protocol.Packet

/**
 * Packet layout per gtker/wow_messages CMSG_AUTH_LOGON_CHALLENGE (version 8 for 3.3.5a)
 */
class AuthClientChallenge(
    val accountName: String
) : Packet {
    override val opcode: Int = AuthOpcode.LOGIN_CHALLENGE

    override fun write(writer: ByteWriter) {
        writer.writeByte(opcode)
        writer.writeByte(8) // protocol version (8 for 3.3.5a/AzerothCore)
        
        // Size is 30 bytes + length of account name
        val size = 30 + accountName.length
        writer.writeShort(size)
        
        writer.writeFixedString("WoW", 4)
        writer.writeByte(3) // Version Major
        writer.writeByte(3) // Version Minor
        writer.writeByte(5) // Version Patch
        writer.writeShort(12340) // Build
        writer.writeFixedString("x86", 4)
        writer.writeFixedString("Win", 4)
        writer.writeFixedString("enUS", 4)
        writer.writeInt(0) // Timezone
        writer.writeInt(0) // IP
        writer.writeByte(accountName.length)
        writer.writeBytes(accountName.toByteArray(Charsets.UTF_8))
    }

    private fun ByteWriter.writeFixedString(value: String, length: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val out = ByteArray(length)
        System.arraycopy(bytes, 0, out, 0, minOf(bytes.size, length))
        writeBytes(out)
    }
}
