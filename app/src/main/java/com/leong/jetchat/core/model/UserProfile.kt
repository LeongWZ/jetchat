package com.leong.jetchat.core.model

data class UserProfile(
    val emailLower: String = "",
    val displayName: String? = null,
    val createdAt: com.google.firebase.Timestamp? = null
)
