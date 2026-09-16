package com.guildchat.protocol

sealed class SessionState {
    object Disconnected : SessionState()
    object AuthChallenge : SessionState()
    object AuthProof : SessionState()
    object RealmList : SessionState()
    object WorldConnect : SessionState()
    object CharSelect : SessionState()
    object InWorld : SessionState()
    data class Error(val message: String) : SessionState()
}
