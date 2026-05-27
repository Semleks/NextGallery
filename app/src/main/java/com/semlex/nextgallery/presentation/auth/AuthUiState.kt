package com.semlex.nextgallery.presentation.auth

data class AuthUiState(
    val serverUrl: String = "",
    val isLoading: Boolean = false,
    val isWaitingForBrowser: Boolean = false,
    val errorMessage: String? = null
) {
    val canSubmit: Boolean
        get() = serverUrl.isNotBlank() && !isLoading
}
