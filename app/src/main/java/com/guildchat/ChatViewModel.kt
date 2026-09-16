package com.guildchat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.guildchat.protocol.SessionState
import com.guildchat.protocol.WoWClient
import com.guildchat.protocol.auth.Realm
import com.guildchat.protocol.world.ChatMessage
import com.guildchat.protocol.world.ChatType
import com.guildchat.protocol.world.WorldServerCharEnum
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val client = WoWClient()

    val state: StateFlow<SessionState> = client.state
    val characters: StateFlow<List<WorldServerCharEnum.Character>> = client.characters
    
    private val _realms = MutableStateFlow<List<Realm>>(emptyList())
    val realms: StateFlow<List<Realm>> = _realms.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val masterKey = MasterKey.Builder(application)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        application,
        "wow_chat_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init {
        // Collect messages from the protocol layer
        client.messages.onEach { msg ->
            _chatHistory.value = _chatHistory.value + msg
        }.launchIn(viewModelScope)
    }

    fun getSavedCredentials(): Triple<String, String, String> {
        return Triple(
            sharedPrefs.getString("host", "") ?: "",
            sharedPrefs.getString("user", "") ?: "",
            sharedPrefs.getString("pass", "") ?: ""
        )
    }

    fun login(host: String, user: String, pass: String) {
        sharedPrefs.edit().apply {
            putString("host", host)
            putString("user", user)
            putString("pass", pass)
        }.apply()

        viewModelScope.launch {
            val realmList = client.login(host, user, pass)
            if (realmList != null) {
                _realms.value = realmList
            }
        }
    }

    fun selectRealm(realm: Realm) {
        viewModelScope.launch {
            client.selectRealm(realm)
        }
    }

    fun selectCharacter(character: WorldServerCharEnum.Character) {
        viewModelScope.launch {
            client.selectCharacter(character)
        }
    }

    fun sendMessage(text: String, type: ChatType) {
        viewModelScope.launch {
            client.sendChat(text, type)
        }
    }

    override fun onCleared() {
        client.disconnect()
    }
}
