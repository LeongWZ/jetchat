package com.leong.jetchat.feature_auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leong.jetchat.feature_auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository
): ViewModel() {

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui
    val currentUser = repo.currentUser

    fun onEmailChange(v: String) = _ui.update { it.copy(email = v) }
    fun onPasswordChange(v: String) = _ui.update { it.copy(password = v) }

    fun signIn() = viewModelScope.launch {
        _ui.update { it.copy(isLoading = true, error = null) }
        runCatching { repo.signIn(_ui.value.email, _ui.value.password) }
            .onFailure { e -> _ui.update { it.copy(error = e.message, isLoading = false) } }
            .onSuccess { _ui.update { it.copy(isLoading = false) } }
    }

    fun register() = viewModelScope.launch {
        _ui.update { it.copy(isLoading = true, error = null) }
        runCatching { repo.register(_ui.value.email, _ui.value.password) }
            .onFailure { e -> _ui.update { it.copy(error = e.message, isLoading = false) } }
            .onSuccess { _ui.update { it.copy(isLoading = false) } }
    }

    fun signOut() = repo.signOut()
}