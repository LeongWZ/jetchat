package com.leong.jetchat.feature_chat.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.leong.jetchat.feature_chat.data.ChatRepository
import com.leong.jetchat.feature_users.data.UsersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessageUi(
    val id: String,
    val sender: String,
    val text: String,
    val time: String,
    val isMine: Boolean
)

data class ChatUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val draft: String = "",
    val messages: List<MessageUi> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    private val usersRepo: UsersRepository,
    auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
): ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    private val myUid: String = checkNotNull(auth.currentUser?.uid)

    private val _draft = MutableStateFlow("")
    private val _error = MutableStateFlow<String?>(null)

    fun onDraftChange(v: String) = _draft.update { v }

    val ui: StateFlow<ChatUiState> =
        combine(
            chatRepo.observeMessages(conversationId),
            _draft,
            _error,
        ) { pairs, draft, error ->
            ChatUiState(
                isLoading = false,
                draft = draft,
                error = error,
                messages = pairs.map { (id, m) ->
                    val senderDisplay = usersRepo.findEmailByUid(m.senderId) ?: m.senderId
                    MessageUi(
                        id = id,
                        sender = senderDisplay,
                        text = m.text,
                        time = m.createdAt?.toDate()?.toString() ?: "",
                        isMine = myUid == m.senderId
                    )
                }
            )
        }
            .catch { e -> emit(ChatUiState(isLoading = false, error = e.message)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun send() = viewModelScope.launch {
        val text = _draft.value
        if (text.isBlank()) return@launch
        runCatching { chatRepo.sendMessage(conversationId, myUid, text) }
            .onFailure { e -> _error.update { e.message ?: "Failed to send message" } }
            .onSuccess { _draft.update { "" } }
    }
}