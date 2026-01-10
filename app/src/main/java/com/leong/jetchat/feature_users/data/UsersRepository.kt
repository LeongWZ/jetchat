package com.leong.jetchat.feature_users.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsersRepository @Inject constructor(
    private val db: FirebaseFirestore
) {

    suspend fun upsertUserProfile(uid: String, email: String) {
        val emailLower = email.trim().lowercase()

        val userRef = db.collection("users").document(uid)
        val snap = userRef.get().await()

        val data = mutableMapOf<String, Any>(
            "emailLower" to emailLower
        )

        if (!snap.exists()) {
            data["created"] = com.google.firebase.Timestamp.now()
        }

        userRef.set(data, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    suspend fun findUidByEmail(email: String): String? {
        val emailLower = email.trim().lowercase()
        val snap = db.collection("users")
            .whereEqualTo("emailLower", emailLower)
            .limit(1)
            .get()
            .await()

        return snap.documents.firstOrNull()?.id
    }

    suspend fun findEmailByUid(uid: String): String? {
        val snap = db.collection("users")
            .document(uid)
            .get()
            .await()
        return snap.getString("emailLower")
    }
}