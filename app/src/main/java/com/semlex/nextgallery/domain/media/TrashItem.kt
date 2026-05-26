package com.semlex.nextgallery.domain.media

data class TrashItem(
    val id: String,
    val path: String,
    val name: String,
    val originalLocation: String?,
    val deletedAt: String?,
    val contentType: String?,
    val sizeBytes: Long?
)
