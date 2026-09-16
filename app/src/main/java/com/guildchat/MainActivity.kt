@file:OptIn(ExperimentalMaterial3Api::class)

package com.guildchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildchat.protocol.SessionState
import com.guildchat.protocol.auth.Realm
import com.guildchat.protocol.world.ChatMessage
import com.guildchat.protocol.world.ChatType
import com.guildchat.protocol.world.WorldServerCharEnum
import kotlin.OptIn

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    ChatApp()
                }
            }
        }
    }
}

@Composable
fun ChatApp(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val realms by viewModel.realms.collectAsState()
    val characters by viewModel.characters.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()

    val savedCreds = remember { viewModel.getSavedCredentials() }
    var host by remember { mutableStateOf(savedCreds.first.ifBlank { "10.0.2.2" }) }
    var user by remember { mutableStateOf(savedCreds.second) }
    var pass by remember { mutableStateOf(savedCreds.third) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is SessionState.Disconnected, is SessionState.Error -> {
                LoginScreen(
                    host = host, user = user, pass = pass,
                    error = (state as? SessionState.Error)?.message,
                    onHostChange = { host = it },
                    onUserChange = { user = it },
                    onPassChange = { pass = it },
                    onLogin = { viewModel.login(host, user, pass) }
                )
            }
            is SessionState.AuthChallenge, is SessionState.AuthProof -> {
                LoadingScreen("Authenticating...")
            }
            is SessionState.RealmList -> {
                RealmListScreen(realms, onSelect = { viewModel.selectRealm(it) })
            }
            is SessionState.WorldConnect -> {
                LoadingScreen("Connecting to Realm...")
            }
            is SessionState.CharSelect -> {
                CharacterListScreen(characters, onSelect = { viewModel.selectCharacter(it) })
            }
            is SessionState.InWorld -> {
                ChatScreen(
                    chatHistory = chatHistory,
                    onSendMessage = { msg, type -> viewModel.sendMessage(msg, type) }
                )
            }
        }
    }
}

@Composable
fun ChatScreen(chatHistory: List<ChatMessage>, onSendMessage: (String, ChatType) -> Unit) {
    var textInput by remember { mutableStateOf("") }
    var chatType by remember { mutableStateOf(ChatType.SAY) }
    val listState = rememberLazyListState()

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(chatHistory) { msg ->
                val color = when (msg.type) {
                    ChatType.GUILD -> Color(0xFF40FF40)
                    ChatType.WHISPER, ChatType.WHISPER_INFORM -> Color(0xFFFF80FF)
                    ChatType.SAY -> Color.White
                    ChatType.SYSTEM -> Color.Yellow
                    else -> Color.LightGray
                }
                Text(
                    text = "[${msg.type}] ${msg.message}",
                    color = color,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Divider(color = Color.DarkGray)

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { chatType = if (chatType == ChatType.SAY) ChatType.GUILD else ChatType.SAY },
                colors = ButtonDefaults.buttonColors(containerColor = if (chatType == ChatType.GUILD) Color(0xFF1B5E20) else Color.DarkGray)
            ) {
                Text(chatType.name, style = MaterialTheme.typography.labelSmall)
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
            )

            IconButton(onClick = {
                if (textInput.isNotBlank()) {
                    onSendMessage(textInput, chatType)
                    textInput = ""
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Cyan)
            }
        }
    }
}

@Composable
fun LoginScreen(host: String, user: String, pass: String, error: String?, onHostChange: (String) -> Unit, onUserChange: (String) -> Unit, onPassChange: (String) -> Unit, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("GuildChat", style = MaterialTheme.typography.headlineLarge, color = Color.Yellow)
        Text("WotLK 3.3.5a", style = MaterialTheme.typography.labelMedium)
        
        Spacer(Modifier.height(24.dp))
        
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
        }

        TextField(host, onHostChange, label = { Text("Server Address") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        TextField(user, onUserChange, label = { Text("Account Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        TextField(pass, onPassChange, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        
        Button(onLogin, Modifier.padding(top = 24.dp).fillMaxWidth()) {
            Text("Enter World")
        }
    }
}

@Composable
fun RealmListScreen(realms: List<Realm>, onSelect: (Realm) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Realms", style = MaterialTheme.typography.headlineMedium)
        LazyColumn {
            items(realms) { realm ->
                Card(
                    onClick = { onSelect(realm) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(realm.name) },
                        trailingContent = { Text("${realm.numCharacters} Chars") }
                    )
                }
            }
        }
    }
}

@Composable
fun CharacterListScreen(characters: List<WorldServerCharEnum.Character>, onSelect: (WorldServerCharEnum.Character) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Characters", style = MaterialTheme.typography.headlineMedium)
        LazyColumn {
            items(characters) { char ->
                Card(
                    onClick = { onSelect(char) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(char.name, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("Level ${char.level}") }
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingScreen(label: String) {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Color.Yellow)
        Text(label, modifier = Modifier.padding(top = 16.dp))
    }
}
