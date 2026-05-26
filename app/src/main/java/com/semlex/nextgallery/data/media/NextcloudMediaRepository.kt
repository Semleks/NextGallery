package com.semlex.nextgallery.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.semlex.nextgallery.data.network.NextcloudWebDavClient
import com.semlex.nextgallery.domain.auth.NextcloudCredentials
import com.semlex.nextgallery.domain.media.MediaItem
import com.semlex.nextgallery.domain.media.TrashItem
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class NextcloudMediaRepository(
    private val context: Context,
    private val webDavClient: NextcloudWebDavClient
) {
    suspend fun verifyConnection(credentials: NextcloudCredentials) {
        webDavClient.list(credentials, "")
    }

    suspend fun deleteImages(
        credentials: NextcloudCredentials,
        images: List<MediaItem>
    ) {
        images.forEach { image ->
            webDavClient.delete(credentials, image.path)
        }
    }

    suspend fun uploadImage(credentials: NextcloudCredentials, uri: Uri) {
        val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val fileName = uri.uniqueUploadName(contentType)

        webDavClient.upload(
            credentials = credentials,
            fileName = fileName,
            body = uri.asRequestBody(contentType)
        )
    }

    suspend fun loadTrash(credentials: NextcloudCredentials): List<TrashItem> {
        return webDavClient.listTrash(credentials)
            .filter { !it.isDirectory }
            .map {
                TrashItem(
                    id = it.path,
                    path = it.path,
                    name = it.trashbinFilename ?: it.name,
                    originalLocation = it.trashbinOriginalLocation,
                    deletedAt = it.trashbinDeletionTime,
                    contentType = it.contentType,
                    sizeBytes = it.sizeBytes
                )
            }
    }

    suspend fun restoreTrashItems(credentials: NextcloudCredentials, items: List<TrashItem>) {
        items.forEach { item ->
            webDavClient.restoreFromTrash(credentials, item.path)
        }
    }

    suspend fun deleteTrashItems(credentials: NextcloudCredentials, items: List<TrashItem>) {
        items.forEach { item ->
            webDavClient.deleteFromTrash(credentials, item.path)
        }
    }

    suspend fun emptyTrash(credentials: NextcloudCredentials) {
        webDavClient.emptyTrash(credentials)
    }

    suspend fun loadImages(credentials: NextcloudCredentials): List<MediaItem> {
        val rootEntries = webDavClient.list(credentials, "")
        val rootImages = rootEntries
            .filter { it.isImage() }
            .map { it.toMediaItem(credentials) }
            .sortedByDescending { it.lastModified.orEmpty() }

        if (rootImages.isNotEmpty()) {
            return rootImages
        }

        val nestedImages = mutableListOf<MediaItem>()

        for (directory in rootEntries
            .filter { it.isDirectory }
            .take(MAX_TOP_LEVEL_DIRECTORIES)) {
            webDavClient.list(credentials, directory.path)
                .filter { it.isImage() }
                .mapTo(nestedImages) { it.toMediaItem(credentials) }

            if (nestedImages.size >= MAX_IMAGES) {
                break
            }
        }

        return nestedImages
            .take(MAX_IMAGES)
            .sortedByDescending { it.lastModified.orEmpty() }
    }

    private fun com.semlex.nextgallery.data.network.WebDavEntry.toMediaItem(
        credentials: NextcloudCredentials
    ): MediaItem {
        return MediaItem(
            id = path,
            path = path,
            name = name,
            contentType = contentType,
            sizeBytes = sizeBytes,
            lastModified = lastModified,
            previewUrl = webDavClient.previewUrl(credentials, path),
            downloadUrl = webDavClient.downloadUrl(credentials, path)
        )
    }

    private fun com.semlex.nextgallery.data.network.WebDavEntry.isImage(): Boolean {
        val type = contentType.orEmpty().lowercase()
        val lowerName = name.lowercase()

        return type.startsWith("image/") ||
            lowerName.endsWith(".jpg") ||
            lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".png") ||
            lowerName.endsWith(".webp") ||
            lowerName.endsWith(".gif") ||
            lowerName.endsWith(".heic") ||
            lowerName.endsWith(".heif")
    }

    private companion object {
        const val MAX_TOP_LEVEL_DIRECTORIES = 20
        const val MAX_IMAGES = 500
    }

    private fun Uri.uniqueUploadName(contentType: String): String {
        val originalName = displayName()
            ?.takeIf { it.isNotBlank() }
            ?.sanitizeFileName()
        val extension = originalName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?: contentType.defaultExtension()

        val baseName = originalName
            ?.substringBeforeLast('.', missingDelimiterValue = originalName)
            ?.takeIf { it.isNotBlank() }
            ?: "photo"
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

        return "NextGallery-$timestamp-$baseName.$extension"
    }

    private fun String.sanitizeFileName(): String {
        return replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }

    private fun String.defaultExtension(): String {
        return when (this) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> "jpg"
        }
    }

    private fun Uri.displayName(): String? {
        return context.contentResolver
            .query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
    }

    private fun Uri.asRequestBody(contentType: String): RequestBody {
        val resolver = context.contentResolver
        val uri = this

        return object : RequestBody() {
            override fun contentType() = contentType.toMediaTypeOrNull()

            override fun contentLength(): Long {
                return resolver
                    .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                        } else {
                            -1L
                        }
                    }
                    ?.takeIf { it >= 0L }
                    ?: resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                    ?: -1L
            }

            override fun writeTo(sink: BufferedSink) {
                val input = resolver.openInputStream(uri)
                    ?: throw FileNotFoundException("Не удалось открыть выбранное фото")

                input.use {
                    sink.writeAll(it.source())
                }
            }
        }
    }
}
