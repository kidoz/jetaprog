package su.kidoz.jetaprog.app.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.printToString
import java.io.File
import javax.imageio.ImageIO

/** Saves review evidence when the dedicated uiTest task supplies an output directory. */
internal fun SemanticsNodeInteraction.saveUiArtifacts(name: String) {
    val directory = System.getProperty("jetaprog.ui.artifacts")?.let(::File) ?: return
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create UI artifact directory: $directory" }
    File(directory, "$name.semantics.txt").writeText(printToString())
    check(ImageIO.write(captureToImage().toAwtImage(), "png", File(directory, "$name.png"))) {
        "No PNG writer available"
    }
}
