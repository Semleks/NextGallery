package com.semlex.nextgallery.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.semlex.nextgallery.domain.auth.NextcloudCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.credentialsDataStore by preferencesDataStore(name = "nextcloud_credentials")

class CredentialsDataStore(
    private val context: Context
) {
    val credentials: Flow<NextcloudCredentials?> = context.credentialsDataStore.data.map { preferences ->
        val serverUrl = preferences[SERVER_URL].orEmpty()
        val username = preferences[USERNAME].orEmpty()
        val password = preferences[PASSWORD].orEmpty()

        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            null
        } else {
            NextcloudCredentials(
                serverUrl = serverUrl,
                username = username,
                password = password
            )
        }
    }

    suspend fun save(credentials: NextcloudCredentials) {
        context.credentialsDataStore.edit { preferences ->
            preferences[SERVER_URL] = credentials.serverUrl
            preferences[USERNAME] = credentials.username
            preferences[PASSWORD] = credentials.password
        }
    }

    suspend fun clear() {
        context.credentialsDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
    }
}
