package com.guildchat.protocol.auth

/**
 * Data model for a WoW World Realm.
 * Cited Reference: gtker/wow_messages WotLK
 */
data class Realm(
    val id: Int,
    val name: String,
    val address: String, // Format: "IP:Port"
    val numCharacters: Int,
    val type: Int = 0,
    val flags: Int = 0,
    val population: Float = 0f,
    val category: Int = 0
) {
    /**
     * Extracts host and port for the World Server connection.
     */
    fun getAddress(): Pair<String, Int> {
        val parts = address.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toInt() ?: 8085
        return host to port
    }
}
