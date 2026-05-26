package com.semlex.nextgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.semlex.nextgallery.presentation.NextGalleryApp
import com.semlex.nextgallery.ui.theme.NextGalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NextGalleryTheme {
                NextGalleryApp()
            }
        }
    }
}
