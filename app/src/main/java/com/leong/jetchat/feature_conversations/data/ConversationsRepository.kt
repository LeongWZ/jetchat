package com.leong.jetchat.feature_conversations.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.leong.jetchat.core.model.Conversation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationsRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    fun observeConversations(userId: String?): Flow<List<Pair<String, Conversation>>> = callbackFlow {
        val q = db.collection("conversations")
            .whereArrayContains("members", userId ?: "")
            .orderBy("lastMessageAt", com.google.firebase.firestore.Query.Direction.DESCENDING)

        val reg: ListenerRegistration = q.addSnapshotListener { snap, err ->
            if (err != null) {
                close(err); return@addSnapshotListener
            }

            val list = snap?.documents?.mapNotNull { doc ->
                val c = doc.toObject(Conversation::class.java) ?: return@mapNotNull null
                doc.id to c
            } ?: emptyList()

            trySend(list)
        }

        awaitClose { reg.remove() }
    }

    suspend fun getOrCreateDirectConversation(myUid: String, otherUid: String): String {
        val key = if (myUid == otherUid) {
            "${myUid}_self"
        } else {
            val (a, b) = listOf(myUid, otherUid).sorted()
            "${a}_${b}"
        }

        val convoRef = db.collection("conversations").document(key)
        val snap = convoRef.get().await()
        if (snap.exists()) return key

        val now = com.google.firebase.Timestamp.now()
        convoRef.set(
            mapOf(
                "type" to "direct",
                "members" to if (myUid == otherUid) listOf(myUid) else listOf(myUid, otherUid),
                "memberKey" to key,
                "createdAt" to now,
                "lastMessageText" to null,
                "lastMessageAt" to now,
                "lastSenderId" to null
            )
        ).await()

        return key
    }
}
