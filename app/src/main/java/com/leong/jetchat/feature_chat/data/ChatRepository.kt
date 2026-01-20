package com.leong.jetchat.feature_chat.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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
            .orderBy("createdAt", Query.Direction.ASCENDING)
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
        val convoRef = db.collection("conversations")
            .document(conversationId)

        val msgId = convoRef
            .collection("messages")
            .document().id
        val clientId = UUID.randomUUID().toString()

        val msg = mapOf(
            "senderId" to senderId,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp(),
            "clientId" to clientId,
            "isDeleted" to false
        )

        val msgRef = convoRef.collection("messages").document(msgId)

        db.runTransaction { tx ->
            // Write message
            tx.set(msgRef, msg)

            // Update convo preview
            tx.update(
                convoRef,
                mapOf(
                    "lastMessageId" to msgId,
                    "lastMessageText" to text,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastSenderId" to senderId
                )
            )

            null
        }.await()
    }

    /**
     * Overwrite edit.
     * - Updates message text + editedAt (+ editedBy)
     * - If this message is the conversation's lastMessageId, update lastMessageText too.
     *
     * NOTE: we intentionally do NOT change createdAt, and we do not bump ordering.
     */
    suspend fun editMessage(
        conversationId: String,
        messageId: String,
        editorUid: String,
        newText: String
    ) {
        val convoRef = db.collection("conversations").document(conversationId)
        val msgRef = convoRef.collection("messages").document(messageId)

        db.runTransaction { tx ->
            val convoSnap = tx.get(convoRef)
            val lastId = convoSnap.getString("lastMessageId")

            val msgSnap = tx.get(msgRef)
            if (!msgSnap.exists()) throw IllegalStateException("Message not found")

            val isDeleted = msgSnap.getBoolean("isDeleted") == true
            if (isDeleted) throw IllegalStateException("Cannot edit a deleted message")

            val senderId = msgSnap.getString("senderId")
            if (senderId != editorUid) throw IllegalStateException("You can only edit your own messages")

            tx.update(
                msgRef, mapOf(
                    "text" to newText,
                    "editedAt" to FieldValue.serverTimestamp(),
                    "editedBy" to editorUid
                )
            )

            if (lastId == messageId) {
                tx.update(convoRef, mapOf("lastMessageText" to newText))
            }
        }.await()
    }

    /**
     * Soft delete.
     * - Sets isDeleted + deletedAt + deletedBy
     * - Clears text to avoid retaining deleted content
     * - If this message is the convo lastMessageId, set lastMessageText to placeholder
     */
    suspend fun deleteMessage(conversationId: String, messageId: String, deleterUid: String) {
        val convoRef = db.collection("conversations").document(conversationId)
        val msgRef = convoRef.collection("messages").document(messageId)

        db.runTransaction { tx ->
            val convoSnap = tx.get(convoRef)
            val lastId = convoSnap.getString("LastMessageId")

            val msgSnap = tx.get(msgRef)
            if (!msgSnap.exists()) throw IllegalStateException("Message not found")

            val senderId = msgSnap.getString("senderId")
            if (senderId != deleterUid) throw IllegalStateException("You can only delete your own messages")

            val alreadyDeleted = msgSnap.getBoolean("isDeleted") == true
            if (alreadyDeleted) return@runTransaction null

            tx.update(
                msgRef,
                mapOf(
                    "isDeleted" to true,
                    "deletedAt" to FieldValue.serverTimestamp(),
                    "deletedBy" to deleterUid,
                    "text" to ""    // wipe content
                )
            )

            if (lastId == messageId) {
                tx.update(convoRef, mapOf("lastMessageText" to "Message deleted"))
            }

            return@runTransaction null
        }.await()
    }
}