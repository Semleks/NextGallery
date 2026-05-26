package com.semlex.nextgallery.domain.auth

data class NextcloudCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
) {
    val normalizedServerUrl: String
        get() = serverUrl.trim().trimEnd('/')
}
