package com.leong.jetchat.feature_chat.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.leong.jetchat.core.model.Message
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor (
    private val db: FirebaseFirestore
){
    fun observeMessages(conversationId: String, limit: Long = 50): Flow<List<Pair<String, Message>>> = callbackFlow {
        val q = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .limit(limit)

        val reg: ListenerRegistration = q.addSnapshotListener { snap, err ->
            if (err != null) {
                close(err); return@addSnapshotListener
            }

            val list = snap?.documents?.mapNotNull { doc ->
                val m = doc.toObject(Message::class.java) ?: return@mapNotNull null
                doc.id to m
            } ?: emptyList()

            trySend(list)
        }

        awaitClose { reg.remove() }
    }

    suspend fun sendMessage(conversationId: String, senderId: String, text: String) {
        val msgId = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .document().id

        val clientId = UUID.randomUUID().toString()

        val msg = mapOf(
            "senderId" to senderId,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp(),
            "clientId" to clientId
        )

        val convoRef = db.collection("conversations").document(conversationId)
        val msgRef = convoRef.collection("messages").document(msgId)

        // Write message
        msgRef.set(msg).await()

        convoRef.update(
            mapOf(
                "lastMessageText" to text,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastSenderId" to senderId
            )
        ).await()
    }
}