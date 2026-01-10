package com.leong.jetchat.feature_profile.presentation

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class ProfileUiState(
    val email: String? = null,
    val uid: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: FirebaseAuth
): ViewModel() {

    val ui: ProfileUiState
        get() = ProfileUiState(
            email = auth.currentUser?.email,
            uid = auth.currentUser?.uid
        )

    fun signOut() = auth.signOut()
}