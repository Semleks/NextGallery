package com.semlex.nextgallery.presentation.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semlex.nextgallery.data.auth.CredentialsDataStore
import com.semlex.nextgallery.data.media.NextcloudMediaRepository
import com.semlex.nextgallery.domain.media.MediaItem
import com.semlex.nextgallery.domain.media.TrashItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.Credentials
import java.io.IOException

class GalleryViewModel(
    private val credentialsDataStore: CredentialsDataStore,
    private val mediaRepository: NextcloudMediaRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                val credentials = credentialsDataStore.credentials.first()
                    ?: error("Сначала нужно подключить Nextcloud")

                val items = withTimeout(GALLERY_LOAD_TIMEOUT_MS) {
                    mediaRepository.loadImages(credentials)
                }
                GalleryUiState(
                    isLoading = false,
                    items = items,
                    authHeader = Credentials.basic(credentials.username, credentials.password)
                )
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.toGalleryErrorMessage()
                    )
                }
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            credentialsDataStore.clear()
            _uiState.value = GalleryUiState(isLoading = false)
            onLoggedOut()
        }
    }

    fun deleteImages(images: List<MediaItem>) {
        if (images.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }

            runCatching {
                val credentials = credentialsDataStore.credentials.first()
                    ?: error("Сначала нужно подключить Nextcloud")

                withTimeout(DELETE_TIMEOUT_MS) {
                    mediaRepository.deleteImages(credentials, images)
                }
            }.onSuccess {
                val deletedIds = images.mapTo(mutableSetOf()) { it.id }
                _uiState.update { state ->
                    state.copy(items = state.items.filterNot { it.id in deletedIds })
                }
                loadTrash()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.toGalleryErrorMessage())
                }
            }
        }
    }

    fun uploadImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorMessage = null) }

            runCatching {
                val credentials = credentialsDataStore.credentials.first()
                    ?: error("Сначала нужно подключить Nextcloud")

                withTimeout(UPLOAD_TIMEOUT_MS) {
                    mediaRepository.uploadImage(credentials, uri)
                }
            }.onSuccess {
                _uiState.update { it.copy(isUploading = false) }
                refresh()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = throwable.toGalleryErrorMessage()
                    )
                }
            }
        }
    }

    fun loadTrash() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTrashLoading = true, errorMessage = null) }

            runCatching {
                val credentials = credentialsDataStore.credentials.first()
                    ?: error("Сначала нужно подключить Nextcloud")

                withTimeout(GALLERY_LOAD_TIMEOUT_MS) {
                    mediaRepository.loadTrash(credentials)
                }
            }.onSuccess { trashItems ->
                _uiState.update {
                    it.copy(
                        trashItems = trashItems,
                        isTrashLoading = false
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isTrashLoading = false,
                        errorMessage = throwable.toGalleryErrorMessage()
                    )
                }
            }
        }
    }

    fun restoreTrashItems(items: List<TrashItem>) {
        if (items.isEmpty()) return

        viewModelScope.launch {
            runCatching {
                val credentials = credentialsDataStore.credentials.first()
                    ?: error("Сначала нужно подключить Nextcloud")

                withTimeout(DELETE_TIMEOUT_MS) {
                    mediaRepository.restoreTrashItems(credentials, items)
                }
            }.onSuccess {
                loadTrash()
                refresh()
            }.onFailure { throwable ->
                _uiState.update { it.copy(errorMessage = throwable.toGalleryErrorMessage()) }
            }
        }
    }

    fun deleteTrashItems(items: List<TrashItem>) {
        if (items.isEmpty()) return

        viewModelScope.launch {
            runCatching {
                val credentials = credentialsDataStore.credentials.first()
                    ?: error("Сначала нужно подключить Nextcloud")

                withTimeout(DELETE_TIMEOUT_MS) {
                    mediaRepository.deleteTrashItems(credentials, items)
                }
            }.onSuccess {
                val deletedIds = items.mapTo(mutableSetOf()) { it.id }
                _uiState.update { state ->
                    state.copy(trashItems = state.trashItems.filterNot { it.id in deletedIds })
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(errorMessage = throwable.toGalleryErrorMessage()) }
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            runCatching {
                val credentials = credentialsDataStore.credentials.first()
                    ?: error("Сначала нужно подключить Nextcloud")

                withTimeout(DELETE_TIMEOUT_MS) {
                    mediaRepository.emptyTrash(credentials)
                }
            }.onSuccess {
                _uiState.update { it.copy(trashItems = emptyList()) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(errorMessage = throwable.toGalleryErrorMessage()) }
            }
        }
    }

    private fun Throwable.toGalleryErrorMessage(): String {
        return when (this) {
            is TimeoutCancellationException -> "Загрузка заняла слишком много времени. Попробуй еще раз"
            is IOException -> "Nextcloud не отвечает. Проверь сеть или адрес сервера"
            else -> message ?: "Не удалось загрузить галерею"
        }
    }

    private companion object {
        const val GALLERY_LOAD_TIMEOUT_MS = 15_000L
        const val DELETE_TIMEOUT_MS = 15_000L
        const val UPLOAD_TIMEOUT_MS = 30_000L
    }
}
