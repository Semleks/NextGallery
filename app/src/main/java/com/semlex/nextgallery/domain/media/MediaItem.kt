package com.semlex.nextgallery.domain.media

data class MediaItem(
    val id: String,
    val path: String,
    val name: String,
    val contentType: String?,
    val sizeBytes: Long?,
    val lastModified: String?,
    val previewUrl: String,
    val downloadUrl: String
)
