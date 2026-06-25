package su.kidoz.jetaprog.app.gallery

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme

/**
 * Dev-only entry point for the [ComponentGallery] — a Storybook-style window.
 *
 * Run this `main()` from the IDE (green gutter arrow) to browse every reusable
 * composable and design token in one place. Not part of the shipped app.
 */
public fun main(): Unit =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "JetaProg — Component Gallery",
            state = rememberWindowState(),
        ) {
            JetaProgTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ComponentGallery()
                }
            }
        }
    }
