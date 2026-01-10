package com.leong.jetchat.feature_conversations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.leong.jetchat.feature_conversations.data.ConversationsRepository
import com.leong.jetchat.feature_users.data.UsersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val usersRepo: UsersRepository,
    private val convRepo: ConversationsRepository,
    private val auth: FirebaseAuth
): ViewModel() {

    private val _email = MutableStateFlow<String>("")
    val email: StateFlow<String> = _email
    fun onEmailChange(v: String) = _email.update { v }

    // Emits conversationId
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun startChat() = viewModelScope.launch {
        val myUid = auth.currentUser?.uid ?: run {
            _error.update { "Not signed in" }
            return@launch
        }

        val input = _email.value.trim()
        if (input.isBlank()) {
            _error.update { "Enter an email" }
            return@launch
        }

        _error.value = null

        val otherUid = usersRepo.findUidByEmail(input)
            ?: run {
                _error.update { "User not found" }
                return@launch
            }

        try {
            val convoId = convRepo.getOrCreateDirectConversation(myUid, otherUid)
            _events.emit(convoId)
        } catch (e: Exception) {
            _error.update { e.message ?: "Failed to start chat" }
        }
    }
}