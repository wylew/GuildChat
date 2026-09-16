package com.guildchat.protocol.auth

import com.guildchat.protocol.ByteReader

/**
 * Parsed response for SMSG_REALM_LIST (0x10).
 * Reference: gtker/wow_messages WotLK and tuicraft/src/auth
 */
class AuthServerRealmList(reader: ByteReader) {

    val realms = mutableListOf<Realm>()

    init {
        // SMSG_REALM_LIST layout: [u8] opcode, [u16] size, [u32] reserved, [u16] count
        val opcode = reader.readByte()
        if (opcode == AuthOpcode.REALM_LIST) {
            reader.readShort() // skip size (u16)
            reader.readInt()   // skip reserved (u32)
            val realmCount = reader.readShort() // count (u16)

            for (i in 0 until realmCount) {
                val type = reader.readByte()
                val lock = reader.readByte()
                val flags = reader.readByte()
                val name = reader.readString()
                val address = reader.readString()
                val population = java.lang.Float.intBitsToFloat(reader.readInt())
                val numCharacters = reader.readByte()
                val category = reader.readByte()
                val id = reader.readByte()
                
                realms.add(Realm(
                    id = id.toInt(),
                    name = name,
                    address = address,
                    numCharacters = numCharacters.toInt(),
                    type = type.toInt(),
                    flags = flags.toInt(),
                    population = population,
                    category = category.toInt()
                ))
            }
            
            // Footer: two null bytes (0x00, 0x00)
            if (reader.remaining() >= 2) {
                reader.readShort()
            }
        }
    }
}
