package com.guildchat.protocol.auth

object AuthOpcode {
    const val LOGIN_CHALLENGE = 0x00
    const val LOGIN_PROOF = 0x01
    const val REALM_LIST = 0x10
}
