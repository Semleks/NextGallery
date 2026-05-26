package com.semlex.nextgallery.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.semlex.nextgallery.data.AppContainer
import com.semlex.nextgallery.domain.auth.NextcloudCredentials
import com.semlex.nextgallery.presentation.auth.AuthRoute
import com.semlex.nextgallery.presentation.gallery.GalleryRoute
import com.semlex.nextgallery.presentation.navigation.NextGalleryRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun NextGalleryApp() {
    val context = LocalContext.current
    val appContainer = remember(context) {
        AppContainer(context)
    }
    val navController = rememberNavController()
    var startupState by remember { mutableStateOf<StartupState>(StartupState.Loading) }

    LaunchedEffect(Unit) {
        val credentials = appContainer.credentialsDataStore.credentials.first()
        delay(STARTUP_DELAY_MS)
        startupState = StartupState.Ready(credentials)
    }

    val state = startupState
    if (state is StartupState.Loading) {
        StartupLoading()
        return
    }

    val startDestination = if ((state as StartupState.Ready).credentials == null) {
        NextGalleryRoute.Auth.path
    } else {
        NextGalleryRoute.Gallery.path
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NextGalleryRoute.Auth.path) {
            AuthRoute(
                credentialsDataStore = appContainer.credentialsDataStore,
                mediaRepository = appContainer.mediaRepository,
                onAuthenticated = {
                    navController.navigate(NextGalleryRoute.Gallery.path) {
                        popUpTo(NextGalleryRoute.Auth.path) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(NextGalleryRoute.Gallery.path) {
            GalleryRoute(
                credentialsDataStore = appContainer.credentialsDataStore,
                mediaRepository = appContainer.mediaRepository,
                onLoggedOut = {
                    navController.navigate(NextGalleryRoute.Auth.path) {
                        popUpTo(NextGalleryRoute.Gallery.path) {
                            inclusive = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun StartupLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

private sealed interface StartupState {
    data object Loading : StartupState
    data class Ready(val credentials: NextcloudCredentials?) : StartupState
}

private const val STARTUP_DELAY_MS = 450L
