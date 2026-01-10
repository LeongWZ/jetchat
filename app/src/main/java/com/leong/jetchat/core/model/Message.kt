package com.leong.jetchat.core.model

data class Message(
    val senderId: String = "",
    val text: String = "",
    val createdAt: com.google.firebase.Timestamp? = null,
    val clientId: String? = null
)
