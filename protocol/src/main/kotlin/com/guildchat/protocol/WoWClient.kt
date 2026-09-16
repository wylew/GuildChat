package com.guildchat.protocol

import com.guildchat.protocol.auth.AuthServerRealmList
import com.guildchat.protocol.auth.AuthSession
import com.guildchat.protocol.auth.Realm
import com.guildchat.protocol.world.ChatMessage
import com.guildchat.protocol.world.ChatType
import com.guildchat.protocol.world.WorldServerCharEnum
import com.guildchat.protocol.world.WorldSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Orchestrates the full flow from Auth Server login to entering the game World and Chatting.
 * Reference: Milestones 7 & 8.
 */
class WoWClient {
    private val _state = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val state: StateFlow<SessionState> = _state

    private val _characters = MutableStateFlow<List<WorldServerCharEnum.Character>>(emptyList())
    val characters: StateFlow<List<WorldServerCharEnum.Character>> = _characters

    private val _messages = MutableSharedFlow<ChatMessage>()
    val messages: SharedFlow<ChatMessage> = _messages

    private var authSession: AuthSession? = null
    private var worldSession: WorldSession? = null
    
    private var username: String = ""
    private var sessionKey: ByteArray? = null
    private var authHost: String? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Step 1: Login to Auth Server and get Realm List.
     */
    suspend fun login(host: String, user: String, pass: String): List<Realm>? {
        this.username = user
        this.authHost = host
        val auth = AuthSession(host)
        this.authSession = auth
        
        val realmList = auth.authenticate(user, pass)
        if (realmList == null) {
            _state.value = auth.state.value
            return null
        }
        
        this.sessionKey = auth.sessionKey
        _state.value = SessionState.RealmList
        return realmList.realms
    }

    /**
     * Step 2: Select a realm and establish World connection.
     */
    suspend fun selectRealm(realm: Realm): WorldSession? {
        val key = sessionKey ?: return null
        
        authSession?.disconnect()
        authSession = null
        
        var (host, port) = realm.getAddress()
        
        // Handle common emulator issue: if realm address is 127.0.0.1, it means the server
        // is announcing itself as localhost. From the emulator, we need to use the auth host.
        if ((host == "127.0.0.1" || host == "localhost") && authHost != null) {
            host = authHost!!
        }

        val world = WorldSession(host, port, username, key)
        this.worldSession = world
        
        // Bridge world session state, characters, and messages to the client
        world.state.onEach { newState ->
            _state.value = newState
        }.launchIn(scope)

        world.characters.onEach { list ->
            _characters.value = list
        }.launchIn(scope)

        world.messages.onEach { msg ->
            _messages.emit(msg)
        }.launchIn(scope)
        
        world.connect()
        return world
    }

    /**
     * Step 3: Select a character and enter the world.
     */
    suspend fun selectCharacter(character: WorldServerCharEnum.Character) {
        worldSession?.selectCharacter(character.guid)
    }

    /**
     * Step 4: Send a chat message.
     * @param message The text to send.
     * @param type The channel type (SAY, GUILD, WHISPER, etc).
     * @param target The player name for Whispers or channel name for custom channels.
     */
    suspend fun sendChat(message: String, type: ChatType = ChatType.SAY, target: String = "", channel: String = "") {
        worldSession?.sendChat(type, message, target = target, channel = channel)
    }

    fun disconnect() {
        authSession?.disconnect()
        worldSession?.disconnect()
        _state.value = SessionState.Disconnected
    }
}
