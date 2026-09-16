package com.guildchat.protocol.world

/**
 * World opcodes for 3.3.5a (12340).
 * Reference: gtker/wow_messages
 */
object WorldOpcode {
    const val CMSG_AUTH_SESSION = 0x01ED
    const val SMSG_AUTH_CHALLENGE = 0x01EC
    const val SMSG_AUTH_RESPONSE = 0x01EE
    
    const val CMSG_CHAR_ENUM = 0x0037
    const val SMSG_CHAR_ENUM = 0x003B
    
    const val CMSG_PLAYER_LOGIN = 0x003D
    
    const val SMSG_LOGIN_VERIFY_WORLD = 0x0236
    
    const val CMSG_MESSAGECHAT = 0x0095
    const val SMSG_MESSAGECHAT = 0x0096
    
    const val CMSG_PING = 0x01DC
    const val SMSG_PONG = 0x01DD
    
    const val SMSG_TIME_SYNC_REQ = 0x0390
    const val CMSG_TIME_SYNC_RESP = 0x0391
}
