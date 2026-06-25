package su.kidoz.jetaprog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import su.kidoz.jetaprog.app.ui.MainScreen
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
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
            undecorated = true,
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        IdeTitleBar(
                            app = app,
                            onMinimize = { windowState.isMinimized = true },
                            onToggleMaximize = { toggleMaximize(windowState) },
                            onClose = ::exitApplication,
                        )
                        MainScreen(app)
                    }
                }
            }
        }
    }

private fun toggleMaximize(windowState: WindowState) {
    windowState.placement =
        if (windowState.placement == WindowPlacement.Maximized) {
            WindowPlacement.Floating
        } else {
            WindowPlacement.Maximized
        }
}

/**
 * Custom macOS-style title bar: traffic-light window controls on the left and a
 * centered, dynamic document title. The whole bar is a drag handle for the window.
 */
@Composable
private fun FrameWindowScope.IdeTitleBar(
    app: JetaProgApplication,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    val title = windowTitle(app)
    WindowDraggableArea {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(IntelliJColors.toolWindowHeader),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(IntelliJColors.background),
            )
            Row(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrafficLight(color = Color(0xFFFF5F57), label = "Close", onClick = onClose)
                TrafficLight(color = Color(0xFFFEBC2E), label = "Minimize", onClick = onMinimize)
                TrafficLight(color = Color(0xFF28C840), label = "Zoom", onClick = onToggleMaximize)
            }
            Text(
                text = title,
                color = IntelliJColors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** A single 12dp macOS traffic-light button. */
@Composable
private fun TrafficLight(
    color: Color,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClickLabel = label, onClick = onClick),
    )
}

/** Builds the window title: `project — file` when editing, otherwise a welcome label. */
@Composable
private fun windowTitle(app: JetaProgApplication): String {
    val session by app.session.collectAsState()
    val currentSession = session
    return if (currentSession == null) {
        "JetaProg — Welcome"
    } else {
        sessionTitle(currentSession)
    }
}

@Composable
private fun sessionTitle(session: ProjectSession): String {
    val editorState by session.editorViewModel.state.collectAsState()
    val project = session.projectPath.substringAfterLast('/')
    val fileName = editorState.activeTab?.name
    return if (fileName != null) "$project — $fileName" else project
}
