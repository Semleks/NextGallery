package com.semlex.nextgallery.presentation.gallery

import com.semlex.nextgallery.domain.media.MediaItem
import com.semlex.nextgallery.domain.media.TrashItem

data class GalleryUiState(
    val isLoading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val trashItems: List<TrashItem> = emptyList(),
    val isTrashLoading: Boolean = false,
    val isUploading: Boolean = false,
    val authHeader: String? = null,
    val errorMessage: String? = null
)
