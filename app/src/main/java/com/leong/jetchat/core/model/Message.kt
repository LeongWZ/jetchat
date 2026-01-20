package com.leong.jetchat.core.model
import com.google.firebase.Timestamp

data class Message(
    val senderId: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
    val clientId: String? = null,

    val editedAt: Timestamp? = null,
    val editedBy: String? = null,

    val isDeleted: Boolean = false,
    val deletedAt: Timestamp? = null,
    val deletedBy: String? = null
)
