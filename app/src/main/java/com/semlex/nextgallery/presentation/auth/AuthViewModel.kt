package com.semlex.nextgallery.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semlex.nextgallery.data.auth.CredentialsDataStore
import com.semlex.nextgallery.data.auth.NextcloudLoginFlowClient
import com.semlex.nextgallery.data.media.NextcloudMediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class AuthViewModel(
    private val credentialsDataStore: CredentialsDataStore,
    private val loginFlowClient: NextcloudLoginFlowClient,
    private val mediaRepository: NextcloudMediaRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onServerUrlChange(value: String) {
        _uiState.update {
            it.copy(
                serverUrl = value.trim(),
                errorMessage = null
            )
        }
    }

    fun connect(
        onOpenLoginUrl: (String) -> Unit,
        onAuthenticated: () -> Unit
    ) {
        val state = _uiState.value
        val message = when {
            state.serverUrl.isBlank() -> "Укажи адрес Nextcloud"
            !state.serverUrl.startsWith("https://") && !state.serverUrl.startsWith("http://") ->
                "Адрес должен начинаться с http:// или https://"
            else -> null
        }

        if (message != null) {
            _uiState.update { it.copy(errorMessage = message) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isWaitingForBrowser = false, errorMessage = null) }

            runCatching {
                val flow = loginFlowClient.createLoginFlow(state.serverUrl)
                _uiState.update { it.copy(isWaitingForBrowser = true) }
                onOpenLoginUrl(flow.loginUrl)
                val credentials = loginFlowClient.pollCredentials(flow)
                mediaRepository.verifyConnection(credentials)
                credentialsDataStore.save(credentials)
            }.onSuccess {
                onAuthenticated()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isWaitingForBrowser = false,
                        errorMessage = throwable.toLoginErrorMessage()
                    )
                }
            }
        }
    }

    private fun Throwable.toLoginErrorMessage(): String {
        return when (this) {
            is IOException -> "Не удалось подключиться к серверу. Проверь адрес и интернет"
            else -> message ?: "Не удалось проверить данные входа"
        }
    }
}
