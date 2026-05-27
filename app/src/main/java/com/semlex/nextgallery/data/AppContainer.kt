package com.semlex.nextgallery.data

import android.content.Context
import com.semlex.nextgallery.data.auth.CredentialsDataStore
import com.semlex.nextgallery.data.auth.NextcloudLoginFlowClient
import com.semlex.nextgallery.data.media.NextcloudMediaRepository
import com.semlex.nextgallery.data.network.NextcloudWebDavClient
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val dispatcher = Dispatcher().apply {
        maxRequests = 8
        maxRequestsPerHost = 3
    }

    private val okHttpClient = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val credentialsDataStore = CredentialsDataStore(context.applicationContext)
    val loginFlowClient = NextcloudLoginFlowClient(okHttpClient)

    val mediaRepository = NextcloudMediaRepository(
        context = context.applicationContext,
        webDavClient = NextcloudWebDavClient(okHttpClient)
    )
}
