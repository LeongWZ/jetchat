package com.leong.jetchat.core.model

data class Conversation(
    val members: List<String> = emptyList(),
    val type: String? = null,
    val createdAt: com.google.firebase.Timestamp? = null,
    val lastMessageText: String? = null,
    val lastMessageAt: com.google.firebase.Timestamp? = null,
    val lastSenderId: String? = null
)
