package com.semlex.nextgallery.data.network

import android.net.Uri
import android.util.Xml
import com.semlex.nextgallery.domain.auth.NextcloudCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder

class NextcloudWebDavClient(
    private val okHttpClient: OkHttpClient
) {
    suspend fun list(credentials: NextcloudCredentials, path: String): List<WebDavEntry> = withContext(Dispatchers.IO) {
        val response = executeWithRetry(
            Request.Builder()
                .url(webDavUrl(credentials, path))
                .method("PROPFIND", propfindBody())
                .header("Authorization", Credentials.basic(credentials.username, credentials.password))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .build()
        )

        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException(it.toNextcloudErrorMessage())
            }

            parsePropfindResponse(
                xml = it.body.string(),
                rootPath = path.trim('/'),
                marker = "/remote.php/dav/files/"
            )
        }
    }

    suspend fun delete(credentials: NextcloudCredentials, path: String) = withContext(Dispatchers.IO) {
        val response = executeWithRetry(
            Request.Builder()
                .url(webDavUrl(credentials, path))
                .delete()
                .header("Authorization", Credentials.basic(credentials.username, credentials.password))
                .build()
        )

        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException(it.toNextcloudErrorMessage())
            }
        }
    }

    suspend fun listTrash(credentials: NextcloudCredentials): List<WebDavEntry> = withContext(Dispatchers.IO) {
        val response = executeWithRetry(
            Request.Builder()
                .url(trashUrl(credentials))
                .method("PROPFIND", trashPropfindBody())
                .header("Authorization", Credentials.basic(credentials.username, credentials.password))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .build()
        )

        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException(it.toNextcloudErrorMessage())
            }

            parsePropfindResponse(
                xml = it.body.string(),
                rootPath = "",
                marker = "/remote.php/dav/trashbin/"
            )
        }
    }

    suspend fun restoreFromTrash(credentials: NextcloudCredentials, path: String) = withContext(Dispatchers.IO) {
        val fileName = path.substringAfterLast('/')
        val response = executeWithRetry(
            Request.Builder()
                .url(trashUrl(credentials, path))
                .method("MOVE", null)
                .header("Authorization", Credentials.basic(credentials.username, credentials.password))
                .header("Destination", trashRestoreUrl(credentials, fileName))
                .build()
        )

        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException(it.toNextcloudErrorMessage())
            }
        }
    }

    suspend fun deleteFromTrash(credentials: NextcloudCredentials, path: String) = withContext(Dispatchers.IO) {
        val response = executeWithRetry(
            Request.Builder()
                .url(trashUrl(credentials, path))
                .delete()
                .header("Authorization", Credentials.basic(credentials.username, credentials.password))
                .build()
        )

        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException(it.toNextcloudErrorMessage())
            }
        }
    }

    suspend fun emptyTrash(credentials: NextcloudCredentials) = withContext(Dispatchers.IO) {
        val response = executeWithRetry(
            Request.Builder()
                .url(trashUrl(credentials))
                .delete()
                .header("Authorization", Credentials.basic(credentials.username, credentials.password))
                .build()
        )

        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException(it.toNextcloudErrorMessage())
            }
        }
    }

    suspend fun upload(
        credentials: NextcloudCredentials,
        fileName: String,
        body: RequestBody
    ) = withContext(Dispatchers.IO) {
        val response = executeWithRetry(
            Request.Builder()
                .url(webDavUrl(credentials, fileName))
                .put(body)
                .header("Authorization", Credentials.basic(credentials.username, credentials.password))
                .build()
        )

        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException(it.toNextcloudErrorMessage())
            }
        }
    }

    fun previewUrl(credentials: NextcloudCredentials, path: String): String {
        val encodedPath = URLEncoder
            .encode("/${path.trimStart('/')}", Charsets.UTF_8.name())
            .replace("+", "%20")
        return "${credentials.normalizedServerUrl}/index.php/apps/preview?file=$encodedPath&x=512&y=512&a=1"
    }

    fun downloadUrl(credentials: NextcloudCredentials, path: String): String {
        return webDavUrl(credentials, path)
    }

    private fun webDavUrl(credentials: NextcloudCredentials, path: String): String {
        val encodedUsername = encodePathSegment(credentials.username)
        val encodedPath = path
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { encodePathSegment(it) }

        val suffix = if (encodedPath.isBlank()) "" else "/$encodedPath"
        return "${credentials.normalizedServerUrl}/remote.php/dav/files/$encodedUsername$suffix"
    }

    private fun propfindBody() = """
        <?xml version="1.0" encoding="utf-8" ?>
        <d:propfind xmlns:d="DAV:">
            <d:prop>
                <d:resourcetype />
                <d:getcontenttype />
                <d:getcontentlength />
                <d:getlastmodified />
            </d:prop>
        </d:propfind>
    """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

    private fun trashPropfindBody() = """
        <?xml version="1.0" encoding="utf-8" ?>
        <d:propfind xmlns:d="DAV:" xmlns:nc="http://nextcloud.org/ns">
            <d:prop>
                <d:resourcetype />
                <d:getcontenttype />
                <d:getcontentlength />
                <d:getlastmodified />
                <nc:trashbin-filename />
                <nc:trashbin-original-location />
                <nc:trashbin-deletion-time />
            </d:prop>
        </d:propfind>
    """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

    private fun parsePropfindResponse(
        xml: String,
        rootPath: String,
        marker: String
    ): List<WebDavEntry> {
        val parser = Xml.newPullParser().apply {
            setInput(StringReader(xml))
        }

        val entries = mutableListOf<WebDavEntry>()
        var currentHref: String? = null
        var contentType: String? = null
        var sizeBytes: Long? = null
        var lastModified: String? = null
        var trashbinFilename: String? = null
        var trashbinOriginalLocation: String? = null
        var trashbinDeletionTime: String? = null
        var isDirectory = false
        var currentTag: String? = null
        var insideResponse = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.localName()
                    currentTag = tag

                    when (tag) {
                        "response" -> {
                            insideResponse = true
                            currentHref = null
                            contentType = null
                            sizeBytes = null
                            lastModified = null
                            trashbinFilename = null
                            trashbinOriginalLocation = null
                            trashbinDeletionTime = null
                            isDirectory = false
                        }
                        "collection" -> if (insideResponse) {
                            isDirectory = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideResponse) {
                        val value = parser.text.orEmpty().trim()

                        if (value.isNotBlank()) {
                            when (currentTag) {
                                "href" -> currentHref = value
                        "getcontenttype" -> contentType = value
                        "getcontentlength" -> sizeBytes = value.toLongOrNull()
                        "getlastmodified" -> lastModified = value
                        "trashbin-filename" -> trashbinFilename = value
                        "trashbin-original-location" -> trashbinOriginalLocation = value
                        "trashbin-deletion-time" -> trashbinDeletionTime = value
                    }
                }
            }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.localName()
                    if (tag == "response") {
                        currentHref
                            ?.toFilePath(rootPath, marker)
                            ?.takeIf { it != rootPath }
                            ?.let { path ->
                                entries += WebDavEntry(
                                    path = path,
                                    isDirectory = isDirectory,
                                    contentType = contentType,
                                    sizeBytes = sizeBytes,
                                    lastModified = lastModified,
                                    trashbinFilename = trashbinFilename,
                                    trashbinOriginalLocation = trashbinOriginalLocation,
                                    trashbinDeletionTime = trashbinDeletionTime
                                )
                            }
                        insideResponse = false
                    }
                    currentTag = null
                }
            }
        }

        return entries
    }

    private fun String.toFilePath(rootPath: String, marker: String): String? {
        val decodedHref = Uri.decode(this).trimEnd('/')
        val davIndex = decodedHref.indexOf(marker)
        if (davIndex == -1) return null

        val afterFiles = decodedHref.substring(davIndex + marker.length)
        val withoutUsername = afterFiles.substringAfter('/', missingDelimiterValue = "")
        val withoutTrashRoot = withoutUsername.removePrefix("trash/").removePrefix("restore/")
        return withoutTrashRoot.trim('/').ifBlank { rootPath }
    }

    private fun String.localName(): String = substringAfter(':')

    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun trashUrl(credentials: NextcloudCredentials, path: String = ""): String {
        val encodedUsername = encodePathSegment(credentials.username)
        val encodedPath = path
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { encodePathSegment(it) }
        val suffix = if (encodedPath.isBlank()) "" else "/$encodedPath"
        return "${credentials.normalizedServerUrl}/remote.php/dav/trashbin/$encodedUsername/trash$suffix"
    }

    private fun trashRestoreUrl(credentials: NextcloudCredentials, fileName: String): String {
        val encodedUsername = encodePathSegment(credentials.username)
        val encodedFileName = encodePathSegment(fileName)
        return "${credentials.normalizedServerUrl}/remote.php/dav/trashbin/$encodedUsername/restore/$encodedFileName"
    }

    private suspend fun executeWithRetry(request: Request): Response {
        repeat(MAX_RATE_LIMIT_RETRIES) { attempt ->
            val response = okHttpClient.newCall(request).execute()
            if (response.code != HTTP_TOO_MANY_REQUESTS) {
                return response
            }

            val delayMs = response.retryAfterDelayMs(attempt)
            response.close()
            delay(delayMs)
        }

        return okHttpClient.newCall(request).execute()
    }

    private fun Response.retryAfterDelayMs(attempt: Int): Long {
        return header("Retry-After")
            ?.toLongOrNull()
            ?.coerceAtLeast(1L)
            ?.times(1_000L)
            ?: (1_500L * (attempt + 1))
    }

    private fun okhttp3.Response.toNextcloudErrorMessage(): String {
        return when (code) {
            401, 403 -> "Nextcloud не принял логин или пароль приложения"
            404 -> "WebDAV-путь не найден. Проверь адрес сервера и логин"
            429 -> "Nextcloud временно ограничил частые запросы. Подожди немного и обнови"
            else -> "Nextcloud вернул HTTP $code"
        }
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_RATE_LIMIT_RETRIES = 3
    }
}
