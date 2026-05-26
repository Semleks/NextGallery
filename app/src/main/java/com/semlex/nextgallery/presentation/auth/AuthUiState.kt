package com.semlex.nextgallery.presentation.auth

data class AuthUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val canSubmit: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !isLoading
}
