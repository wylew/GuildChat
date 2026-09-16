package com.guildchat.protocol.auth

import com.guildchat.protocol.ByteWriter
import com.guildchat.protocol.Packet

/**
 * Packet layout per gtker/wow_messages CMSG_REALM_LIST
 */
class AuthClientRealmList : Packet {
    override val opcode: Int = AuthOpcode.REALM_LIST

    override fun write(writer: ByteWriter) {
        writer.writeByte(opcode)
        writer.writeInt(0) // Padding/Reserved
    }
}
