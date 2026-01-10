package com.leong.jetchat.feature_conversations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.leong.jetchat.core.model.Conversation
import com.leong.jetchat.feature_conversations.data.ConversationsRepository
import com.leong.jetchat.feature_users.data.UsersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ConversationRowUi(
    val id: String,
    val title: String,
    val last: String,
    val time: String
)

data class ConversationListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val items: List<ConversationRowUi> = emptyList()
)

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val convRepo: ConversationsRepository,
    private val usersRepo: UsersRepository,
    private val auth: FirebaseAuth
): ViewModel() {

    val ui: StateFlow<ConversationListUiState> =
        convRepo.observeConversations(checkNotNull(auth.currentUser?.uid))
            .map { pairs ->
                ConversationListUiState(
                    isLoading = false,
                    items = pairs.map { (id, c) ->
                        val title = getConversationTitle(c)
                        ConversationRowUi(
                            id = id,
                            title = title,
                            last = c.lastMessageText ?: "",
                            time = c.lastMessageAt?.toDate()?.toString() ?: ""
                        )
                    }
                )
            }
            .catch { e ->
                emit(ConversationListUiState(isLoading = false, error = e.message))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationListUiState())

    private suspend fun getConversationTitle(c: Conversation): String {
        if (c.type == "direct" && c.members == listOfNotNull(auth.currentUser?.uid)) {
            return auth.currentUser?.email ?: "Conversations"
        }

        if (c.type == "direct" ) {
            val otherUid = c.members.firstOrNull { it != auth.currentUser?.uid } ?: return "Conversation"
            return usersRepo.findEmailByUid(otherUid) ?: "Conversation"
        }

        return "Conversation"
    }
}
