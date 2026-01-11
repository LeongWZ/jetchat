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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class MessageUi(
    val id: String,
    val sender: String,
    val text: String,
    val time: String,
    val isMine: Boolean
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    private val usersRepo: UsersRepository,
    auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    private val myUid: String = checkNotNull(auth.currentUser?.uid)

    // Individual states

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isMessagesLoading = MutableStateFlow(true)

    /**
     * Cache of sender display values, keyed by uid.
     *
     * - Key:   uid
     * - Value: display string (email or uid fallback) when resolved, or `null` while a fetch is in progress.
     *
     * Note: If a user document does not exist, we fall back to using the uid as the display string,
     * so the cache will eventually contain a non-null value for that uid; `null` only represents
     * a pending/ongoing fetch, never "user doesn't exist".
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

        // mark as pending immediately so we can show senders-loading
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
            .catch { e ->
                _error.value = e.message ?: "Failed to load messages"
            }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    // Track which senderIds are currently needed (based on current messages)
    private val neededSenderIds: StateFlow<Set<String>> =
        rawMessages
            .map { pairs -> pairs.map { (_, m) -> m.senderId }.toSet() }
            .onEach { ids ->
                // kick off lazy resolution only for ids we actually need
                ids.forEach { ensureSender(it) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        // messages loading ends on first snapshot (even if empty)
        viewModelScope.launch {
            rawMessages.take(1).collect { _isMessagesLoading.value = false }
        }
    }

    // sender-cache loading: true if any needed sender is still pending
    val isSendersLoading: StateFlow<Boolean> =
        combine(neededSenderIds, senderCache) { ids, cache ->
            ids.any { uid -> cache[uid] == null }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // overall loading (what you show as spinner)
    val isLoading: StateFlow<Boolean> =
        combine(_isMessagesLoading, isSendersLoading) { msgsLoading, sendersLoading ->
            msgsLoading || sendersLoading
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val messagesUi: StateFlow<List<MessageUi>> =
        combine(rawMessages, senderCache) { pairs, cache ->
            pairs.map { (id, m) ->
                val senderDisplay = cache[m.senderId] ?: m.senderId // pending -> show uid for now

                MessageUi(
                    id = id,
                    sender = senderDisplay,
                    text = m.text,
                    time = m.createdAt?.toDate()?.toString() ?: "",
                    isMine = (myUid == m.senderId)
                )
            }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onDraftChange(v: String) {
        _draft.value = v
    }

    fun send() = viewModelScope.launch {
        val text = _draft.value
        if (text.isBlank()) return@launch

        runCatching { chatRepo.sendMessage(conversationId, myUid, text) }
            .onFailure { e -> _error.value = e.message ?: "Failed to send message" }
            .onSuccess { _draft.value = "" }
    }
}
