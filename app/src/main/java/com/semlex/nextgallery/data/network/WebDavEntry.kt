package com.semlex.nextgallery.data.network

data class WebDavEntry(
    val path: String,
    val isDirectory: Boolean,
    val contentType: String?,
    val sizeBytes: Long?,
    val lastModified: String?,
    val trashbinFilename: String? = null,
    val trashbinOriginalLocation: String? = null,
    val trashbinDeletionTime: String? = null
) {
    val name: String
        get() = path.trimEnd('/').substringAfterLast('/')
}
