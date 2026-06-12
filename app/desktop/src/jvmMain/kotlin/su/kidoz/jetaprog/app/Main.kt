package su.kidoz.jetaprog.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import su.kidoz.jetaprog.app.ui.MainScreen
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme

/**
 * Main entry point for the JetaProg IDE desktop application.
 */
public fun main(): Unit =
    application {
        val windowState = rememberWindowState()
        val iconPainter =
            remember {
                val iconBytes =
                    Thread
                        .currentThread()
                        .contextClassLoader
                        .getResourceAsStream("icon.png")
                        ?.readBytes()
                iconBytes?.let {
                    BitmapPainter(Image.makeFromEncoded(it).toComposeImageBitmap())
                }
            }

        Window(
            onCloseRequest = ::exitApplication,
            title = "JetaProg",
            state = windowState,
            icon = iconPainter,
        ) {
            val app = remember { JetaProgApplication() }

            // Initialize the application (registers language providers, etc.)
            LaunchedEffect(Unit) {
                app.initialize()
            }

            // Clean shutdown on window close
            DisposableEffect(Unit) {
                onDispose {
                    runBlocking { app.shutdown() }
                }
            }

            JetaProgTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(app)
                }
            }
        }
    }
