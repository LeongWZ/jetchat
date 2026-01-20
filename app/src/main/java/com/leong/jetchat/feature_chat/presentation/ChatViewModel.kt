package com.leong.jetchat.feature_chat.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.leong.jetchat.core.model.Message
import com.leong.jetchat.feature_chat.data.ChatRepository
import com.leong.jetchat.feature_users.data.UsersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class MessageUi(
    val id: String,
    val sender: String,
    val text: String,
    val time: String,
    val isMine: Boolean,
    val isDeleted: Boolean,
    val isEdited: Boolean
)

sealed interface ComposerMode {
    data object Normal : ComposerMode
    data class Editing(val messageId: String) : ComposerMode
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    private val usersRepo: UsersRepository,
    auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    private val myUid: String = checkNotNull(auth.currentUser?.uid)

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _composerMode = MutableStateFlow<ComposerMode>(ComposerMode.Normal)
    val composerMode: StateFlow<ComposerMode> = _composerMode

    // Message actions UI state
    private val _actionMenuFor = MutableStateFlow<String?>(null) // messageId or null
    val actionMenuFor: StateFlow<String?> = _actionMenuFor

    private val _confirmDeleteFor = MutableStateFlow<String?>(null) // messageId or null
    val confirmDeleteFor: StateFlow<String?> = _confirmDeleteFor

    /**
     * Cache of sender display values, keyed by uid.
     *
     * - Key: uid
     * - Value: display string (email or uid fallback) when resolved,
     *          or `null` while a fetch is in progress.
     */
    private val senderCache = MutableStateFlow<Map<String, String?>>(emptyMap())

    // prevents duplicate concurrent lookups for the same uid
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private fun ensureSender(uid: String) {
        // already have an entry (pending or resolved)
        if (senderCache.value.containsKey(uid)) return

        // ensure only one concurrent lookup per uid
        if (!inFlight.add(uid)) return

        // another thread may have added this uid while we were adding to inFlight
        if (senderCache.value.containsKey(uid)) {
            inFlight.remove(uid)
            return
        }

        // mark as pending immediately
        senderCache.update { it + (uid to null) }

        viewModelScope.launch(Dispatchers.IO) {
            val emailOrNull = runCatching { usersRepo.findEmailByUid(uid) }.getOrNull()
            val display = emailOrNull ?: uid // fallback if user doc missing

            senderCache.update { it + (uid to display) }
            inFlight.remove(uid)
        }
    }

    // Single upstream Firestore listener (shared)
    private val rawMessages: SharedFlow<List<Pair<String, Message>>> =
        chatRepo.observeMessages(conversationId)
            .catch { e -> _error.value = e.message ?: "Failed to load messages" }
            .onEach { pairs ->
                pairs.asSequence()
                    .map { (_, m) -> m.senderId }
                    .distinct()
                    .forEach(::ensureSender)
            }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // overall loading (what you show as spinner)
    val isLoading: StateFlow<Boolean> =
        rawMessages
            .map { false }                 // once we see any emission, loading = false
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // Messages UI: show uid while sender is pending, then update to email when resolved
    val messagesUi: StateFlow<List<MessageUi>> =
        combine(rawMessages, senderCache) { pairs, cache ->
            pairs.map { (id, m) ->
                val senderDisplay = cache[m.senderId] ?: m.senderId // pending -> show uid
                val isDeleted = m.isDeleted || (m.deletedAt != null) || (m.deletedBy != null)
                val isEdited = (m.editedAt != null)

                MessageUi(
                    id = id,
                    sender = senderDisplay,
                    text = if (isDeleted) "Message deleted" else m.text,
                    time = m.createdAt?.toDate()?.toString() ?: "",
                    isMine = (myUid == m.senderId),
                    isDeleted = isDeleted,
                    isEdited = isEdited
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Draft / composer actions ---

    fun onDraftChange(v: String) {
        _draft.value = v
    }

    fun openActions(messageId: String) {
        _actionMenuFor.value = messageId
    }

    fun dismissActions() {
        _actionMenuFor.value = null
    }

    fun requestDelete(messageId: String) {
        _actionMenuFor.value = null
        _confirmDeleteFor.value = messageId
    }

    fun cancelDelete() {
        _confirmDeleteFor.value = null
    }

    fun startEdit(messageId: String) {
        val msg = messagesUi.value.firstOrNull { it.id == messageId } ?: run {
            _error.value = "Message not found"
            _actionMenuFor.value = null
            return
        }
        if (!msg.isMine || msg.isDeleted) {
            _actionMenuFor.value = null
            return
        }

        _composerMode.value = ComposerMode.Editing(messageId)
        _draft.value = msg.text
        _actionMenuFor.value = null
    }

    fun cancelEdit() {
        _composerMode.value = ComposerMode.Normal
        _draft.value = ""
    }

    fun sendOrSave() = viewModelScope.launch {
        val text = _draft.value
        if (text.isBlank()) return@launch

        when (val mode = _composerMode.value) {
            is ComposerMode.Normal -> {
                runCatching { chatRepo.sendMessage(conversationId, myUid, text) }
                    .onFailure { e -> _error.value = e.message ?: "Failed to send message" }
                    .onSuccess { _draft.value = "" }
            }
            is ComposerMode.Editing -> {
                runCatching { chatRepo.editMessage(conversationId, mode.messageId, myUid, text) }
                    .onFailure { e -> _error.value = e.message ?: "Failed to edit message" }
                    .onSuccess {
                        _composerMode.value = ComposerMode.Normal
                        _draft.value = ""
                    }
            }
        }
    }

    fun confirmDelete() = viewModelScope.launch {
        val id = _confirmDeleteFor.value ?: return@launch
        _confirmDeleteFor.value = null

        // If user is editing this message, exit edit mode
        val mode = _composerMode.value
        if (mode is ComposerMode.Editing && mode.messageId == id) {
            _composerMode.value = ComposerMode.Normal
            _draft.value = ""
        }

        runCatching { chatRepo.deleteMessage(conversationId, id, myUid) }
            .onFailure { e -> _error.value = e.message ?: "Failed to delete message" }
    }
}
