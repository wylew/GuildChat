package com.guildchat.protocol.auth

import com.guildchat.protocol.ByteReader

/**
 * Parsed response for SMSG_AUTH_LOGON_CHALLENGE (0x00)
 * Reference: gtker/wow_messages WotLK and tuicraft/src/auth
 */
class AuthServerChallenge(reader: ByteReader) {
    val opcode: Int = reader.readByte()      // Byte 0
    val unused: Int = reader.readByte()      // Byte 1
    val protocolResult: Int = reader.readByte() // Byte 2
    
    var serverPublicKey: ByteArray = ByteArray(32)
    var generator: ByteArray = ByteArray(0)
    var modulus: ByteArray = ByteArray(0)
    var salt: ByteArray = ByteArray(32)
    var crcSalt: ByteArray = ByteArray(16)
    var securityFlags: Int = 0

    init {
        // If SUCCESS, B starts at Byte 3
        if (opcode == AuthOpcode.LOGIN_CHALLENGE && protocolResult == 0) {
            serverPublicKey = reader.readBytes(32)
            
            val gLen = reader.readByte()
            generator = reader.readBytes(gLen)
            
            val nLen = reader.readByte()
            modulus = reader.readBytes(nLen)
            
            salt = reader.readBytes(32)
            crcSalt = reader.readBytes(16)
            securityFlags = reader.readByte()
        }
    }
}
