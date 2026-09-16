package com.guildchat.protocol.auth

import com.guildchat.protocol.ByteReader

/**
 * Parsed response for SMSG_AUTH_LOGON_PROOF (0x01)
 * Reference: gtker/wow_messages
 */
class AuthServerProof(reader: ByteReader) {
    val opcode: Int = reader.readByte()
    val error: Int = reader.readByte()
    var M2: ByteArray = ByteArray(0)
    var accountFlags: Int = 0
    var surveyId: Int = 0
    var loginFlags: Int = 0

    init {
        if (opcode == AuthOpcode.LOGIN_PROOF && error == 0) {
            M2 = reader.readBytes(20)
            accountFlags = reader.readInt()
            surveyId = reader.readInt()
            loginFlags = reader.readShort()
        }
    }
}
