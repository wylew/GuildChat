package com.guildchat.protocol.world

import com.guildchat.protocol.ByteReader
import com.guildchat.protocol.ByteWriter
import com.guildchat.protocol.Packet

/**
 * SMSG_AUTH_CHALLENGE (0x01EC)
 */
class WorldServerAuthChallenge(reader: ByteReader) {
    /**
     * Standard WotLK 3.3.5a:
     * uint32 serverSeed
     * 
     * Note: Some custom servers or different expansions might have a 'method' field.
     * We check the payload size to remain compatible.
     */
    val serverSeed: Int = if (reader.remaining() >= 8) {
        reader.readInt() // Skip method/padding
        reader.readInt()
    } else {
        reader.readInt()
    }
}

/**
 * CMSG_AUTH_SESSION (0x01ED)
 * Reference: tuicraft/src/world/session.ts and gtker/wow_messages
 */
class WorldClientAuthSession(
    val build: Int,
    val serverId: Int,
    val account: String,
    val clientSeed: Int,
    val digest: ByteArray 
) : Packet {
    override val opcode: Int = WorldOpcode.CMSG_AUTH_SESSION

    override fun write(writer: ByteWriter) {
        writer.writeInt(build)
        writer.writeInt(serverId)
        writer.writeString(account)
        writer.writeInt(0) // login_server_id
        writer.writeInt(clientSeed)
        writer.writeInt(0) // region_id
        writer.writeInt(0) // battle_group_id
        writer.writeInt(0) // realm_id
        writer.writeLong(0) // dos_response (u64)
        writer.writeBytes(digest)
        writer.writeInt(0) // addon_info size
    }
}

/**
 * SMSG_AUTH_RESPONSE (0x01EE)
 * Reference: gtker/wow_messages WotLK
 */
class WorldServerAuthResponse(reader: ByteReader) {
    val result: Int = reader.readByte()
    init {
        if (result == 0x0C) { // AUTH_OK
            reader.readInt() // billing_time_remaining
            reader.readByte() // billing_flags
            reader.readInt() // billing_time_rested
            reader.readByte() // expansion
        }
    }
}

/**
 * CMSG_CHAR_ENUM (0x0037)
 */
class WorldClientCharEnum : Packet {
    override val opcode: Int = WorldOpcode.CMSG_CHAR_ENUM
    override fun write(writer: ByteWriter) {}
}

/**
 * SMSG_CHAR_ENUM (0x003B)
 * Reference: gtker/wow_messages WotLK (23 visible equipment slots)
 */
class WorldServerCharEnum(reader: ByteReader) {
    val characters = mutableListOf<Character>()

    data class Character(
        val guid: Long,
        val name: String,
        val race: Int,
        val clazz: Int,
        val gender: Int,
        val level: Int,
        val zoneId: Int,
        val mapId: Int
    )

    init {
        val count = reader.readByte()
        for (i in 0 until count) {
            val guid = reader.readLong()
            val name = reader.readString()
            val race = reader.readByte()
            val clazz = reader.readByte()
            val gender = reader.readByte()
            
            // Cosmetics: skin, face, hairStyle, hairColor, facialHair
            repeat(5) { reader.readByte() }
            
            val level = reader.readByte()
            val zoneId = reader.readInt()
            val mapId = reader.readInt()
            
            // Position (3 floats)
            repeat(3) { reader.readInt() }
            
            reader.readInt() // guild_id
            reader.readInt() // char_flags
            reader.readInt() // customization_flags (u32 in WotLK)
            reader.readByte() // first_login
            
            reader.readInt() // pet_display_id
            reader.readInt() // pet_level
            reader.readInt() // pet_family
            
            // Visible Equipment (23 slots in 3.3.5a)
            repeat(23) {
                reader.readInt()  // display_id
                reader.readByte() // inventory_type
                reader.readInt()  // enchant_id
            }

            characters.add(Character(guid, name, race, clazz, gender, level, zoneId, mapId))
        }
    }
}

/**
 * CMSG_PLAYER_LOGIN (0x003D)
 */
class WorldClientPlayerLogin(val guid: Long) : Packet {
    override val opcode: Int = WorldOpcode.CMSG_PLAYER_LOGIN
    override fun write(writer: ByteWriter) {
        writer.writeLong(guid)
    }
}

/**
 * SMSG_LOGIN_VERIFY_WORLD (0x0236)
 */
class WorldServerLoginVerifyWorld(reader: ByteReader) {
    val mapId: Int = reader.readInt()
}

/**
 * SMSG_TIME_SYNC_REQ (0x0390)
 */
class WorldServerTimeSyncRequest(reader: ByteReader) {
    val counter: Int = reader.readInt()
}

/**
 * CMSG_TIME_SYNC_RESP (0x0391)
 */
class WorldClientTimeSyncResponse(val counter: Int, val ticks: Int) : Packet {
    override val opcode: Int = WorldOpcode.CMSG_TIME_SYNC_RESP
    override fun write(writer: ByteWriter) {
        writer.writeInt(counter)
        writer.writeInt(ticks)
    }
}

/**
 * SMSG_PONG (0x01DD)
 */
class WorldServerPong(reader: ByteReader) {
    val sequence: Int = reader.readInt()
}

/**
 * CMSG_PING (0x01DC)
 */
class WorldClientPing(val sequence: Int) : Packet {
    override val opcode: Int = WorldOpcode.CMSG_PING
    override fun write(writer: ByteWriter) {
        writer.writeInt(sequence)
    }
}
