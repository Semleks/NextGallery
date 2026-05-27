package com.semlex.nextgallery.data.auth

import com.semlex.nextgallery.domain.auth.NextcloudCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class NextcloudLoginFlowClient(
    private val okHttpClient: OkHttpClient
) {
    suspend fun createLoginFlow(serverUrl: String): LoginFlow = withContext(Dispatchers.IO) {
        val normalizedServerUrl = serverUrl.trim().trimEnd('/')
        val response = okHttpClient.newCall(
            Request.Builder()
                .url("$normalizedServerUrl/index.php/login/v2")
                .post(FormBody.Builder().build())
                .build()
        ).execute()

        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Nextcloud вернул HTTP ${it.code}")
            }

            val json = JSONObject(it.body.string())
            val poll = json.getJSONObject("poll")

            LoginFlow(
                loginUrl = json.getString("login"),
                pollEndpoint = poll.getString("endpoint"),
                pollToken = poll.getString("token")
            )
        }
    }

    suspend fun pollCredentials(flow: LoginFlow): NextcloudCredentials = withTimeout(LOGIN_TIMEOUT_MS) {
        while (true) {
            val response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(
                    Request.Builder()
                        .url(flow.pollEndpoint)
                        .post(
                            FormBody.Builder()
                                .add("token", flow.pollToken)
                                .build()
                        )
                        .build()
                ).execute()
            }

            response.use {
                when (it.code) {
                    200 -> {
                        val json = JSONObject(it.body.string())
                        return@withTimeout NextcloudCredentials(
                            serverUrl = json.getString("server"),
                            username = json.getString("loginName"),
                            password = json.getString("appPassword")
                        )
                    }
                    404 -> delay(POLL_INTERVAL_MS)
                    else -> throw IllegalStateException("Nextcloud вернул HTTP ${it.code}")
                }
            }
        }

        error("Авторизация не завершена")
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
        const val LOGIN_TIMEOUT_MS = 20 * 60 * 1_000L
    }
}

data class LoginFlow(
    val loginUrl: String,
    val pollEndpoint: String,
    val pollToken: String
)
