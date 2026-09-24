package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.theme.Dimensions

/** The fixed brand artwork shared by the welcome hub and desktop window icon. */
@Composable
public fun JetaProgLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = "JetaProg",
) {
    Image(
        painter = rememberJetaProgIcon(),
        contentDescription = contentDescription,
        modifier = modifier.size(Dimensions.brandIconSize.dp),
    )
}

/** Loads the bundled brand artwork once per composition, preserving its original colors. */
@Composable
public fun rememberJetaProgIcon(): Painter =
    remember {
        val bytes =
            requireNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")) {
                "Missing bundled JetaProg icon"
            }.use { it.readBytes() }
        BitmapPainter(
            org.jetbrains.skia.Image
                .makeFromEncoded(bytes)
                .toComposeImageBitmap(),
        )
    }
