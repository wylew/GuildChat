package com.guildchat.protocol.world

import com.guildchat.protocol.ByteReader
import com.guildchat.protocol.ByteWriter
import com.guildchat.protocol.Packet

/**
 * Chat types for WoW 3.3.5a.
 * Reference: gtker/wow_messages
 */
enum class ChatType(val value: Int) {
    SAY(0),
    PARTY(1),
    RAID(2),
    GUILD(3),
    OFFICER(4),
    YELL(5),
    WHISPER(6),
    WHISPER_INFORM(7),
    EMOTE(8),
    TEXT_EMOTE(9),
    SYSTEM(10),
    MONSTER_SAY(11),
    MONSTER_YELL(12),
    MONSTER_WHISPER(13),
    MONSTER_EMOTE(14),
    CHANNEL(17),
    AFK(19),
    DND(20),
    IGNORED(21),
    BG_SYSTEM_NEUTRAL(22),
    BG_SYSTEM_ALLIANCE(23),
    BG_SYSTEM_HORDE(24),
    RAID_LEADER(25),
    RAID_WARNING(26),
    RAID_BOSS_WHISPER(27),
    RAID_BOSS_EMOTE(28),
    BATTLEGROUND(31),
    BATTLEGROUND_LEADER(32),
    ACHIEVEMENT(37),
    GUILD_ACHIEVEMENT(38);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: SAY
    }
}

/**
 * Data model for a processed chat message.
 */
data class ChatMessage(
    val type: ChatType,
    val senderGuid: Long,
    val message: String,
    val channelName: String? = null,
    val language: Int = 0,
    val tag: Int = 0
)

/**
 * CMSG_MESSAGECHAT (0x0095)
 * Packet layout per gtker/wow_messages 3.3.5a / AzerothCore
 */
class ChatClientMessage(
    val type: ChatType,
    val language: Int = 0, // 0 = Universal
    val message: String,
    val target: String = "",
    val channel: String = ""
) : Packet {
    override val opcode: Int = WorldOpcode.CMSG_MESSAGECHAT

    override fun write(writer: ByteWriter) {
        writer.writeInt(type.value)
        writer.writeInt(language)
        when (type) {
            ChatType.CHANNEL -> writer.writeString(channel)
            ChatType.WHISPER -> writer.writeString(target)
            else -> {}
        }
        writer.writeString(message)
    }
}

/**
 * SMSG_MESSAGECHAT (0x0096)
 * Packet layout per gtker/wow_messages WotLK (3.3.5a)
 */
class ChatServerMessage(reader: ByteReader) {
    val type: ChatType = ChatType.fromInt(reader.readByte())
    val language: Int = reader.readInt()
    val senderGuid: Long = reader.readLong()
    val flags: Int = reader.readInt() // u32 Flags
    
    val channelName: String? = if (type == ChatType.CHANNEL) {
        reader.readString()
    } else {
        null
    }
    
    val targetGuid: Long = reader.readLong()
    val messageLength: Int = reader.readInt() // u32 message length
    val message: String = reader.readString()
    val tag: Int = reader.readByte().toInt() and 0xFF

    fun toChatMessage(): ChatMessage {
        return ChatMessage(
            type = type,
            senderGuid = senderGuid,
            message = message,
            channelName = channelName,
            language = language,
            tag = tag
        )
    }
}
