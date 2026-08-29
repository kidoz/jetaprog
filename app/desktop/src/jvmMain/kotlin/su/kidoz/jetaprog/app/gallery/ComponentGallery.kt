package su.kidoz.jetaprog.app.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.BuildStatus
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJStatusBar
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.components.PopupListRow
import su.kidoz.jetaprog.app.ui.components.popupChrome
import su.kidoz.jetaprog.app.ui.panels.Avatar
import su.kidoz.jetaprog.app.ui.panels.FileBadge
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** A named color for the palette swatch grid. */
private data class Swatch(
    val name: String,
    val color: Color,
)

@Composable
private fun paletteSwatches(): List<Swatch> {
    val palette = LocalIntelliJColors.current
    return listOf(
        Swatch("background", palette.background),
        Swatch("backgroundDarker", palette.backgroundDarker),
        Swatch("surface", palette.surface),
        Swatch("surfaceElevated", palette.surfaceElevated),
        Swatch("surfaceHover", palette.surfaceHover),
        Swatch("accent", palette.accent),
        Swatch("accentSubtle", palette.accentSubtle),
        Swatch("treeSelectionBackground", palette.treeSelectionBackground),
        Swatch("success", palette.success),
        Swatch("warning", palette.warning),
        Swatch("error", palette.error),
        Swatch("info", palette.info),
        Swatch("iconKotlin", palette.iconKotlin),
        Swatch("iconFolder", palette.iconFolder),
        Swatch("editorCurrentLine", palette.editorCurrentLine),
        Swatch("editorSelectionActive", palette.editorSelectionActive),
    )
}

private val SPACING_STEPS: List<Pair<String, Int>> =
    listOf(
        "xxs" to Spacing.xxs,
        "xs" to Spacing.xs,
        "sm" to Spacing.sm,
        "md" to Spacing.md,
        "lg" to Spacing.lg,
        "xl" to Spacing.xl,
        "xxl" to Spacing.xxl,
    )

/**
 * A "Storybook"-style visual catalog of JetaProg's reusable composables and design tokens.
 *
 * Run [main] in `GalleryMain.kt` to browse it. Use it (and the screenshots) as the shared
 * visual index referenced by `DESIGN_CONTRACT.md`.
 */
@Composable
public fun ComponentGallery(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(LocalIntelliJColors.current.background)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl.dp),
    ) {
        Text(
            text = "JetaProg Component Gallery",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = JetaProgFonts.codeFont,
        )

        GallerySection("Buttons · IntelliJButton") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp)) {
                IntelliJButton(text = "Primary", onClick = {}, style = ButtonStyle.PRIMARY)
                IntelliJButton(text = "Secondary", onClick = {}, style = ButtonStyle.SECONDARY)
                IntelliJButton(text = "Danger", onClick = {}, style = ButtonStyle.DANGER)
                IntelliJButton(text = "With icon", onClick = {}, style = ButtonStyle.PRIMARY, icon = Icons.Default.Add)
                IntelliJButton(text = "Disabled", onClick = {}, enabled = false)
                IntelliJButton(text = "Open", onClick = {}, icon = Icons.Default.FolderOpen)
            }
        }

        GallerySection("Inputs · IntelliJTextField") {
            var text by remember { mutableStateOf("") }
            var errored by remember { mutableStateOf("oops") }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md.dp)) {
                IntelliJTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "Project name",
                    placeholder = "my-project",
                )
                IntelliJTextField(
                    value = errored,
                    onValueChange = { errored = it },
                    label = "With error",
                    error = "Required",
                )
                IntelliJTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "Search files",
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search files",
                            tint = LocalIntelliJColors.current.textSecondary,
                            modifier = Modifier.size(Dimensions.iconMd.dp),
                        )
                    },
                )
            }
        }

        GallerySection("File badges · FileBadge") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp)) {
                listOf("Main.kt", "App.java", "lib.rs", "script.py", "README", "data.json").forEach { name ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
                    ) {
                        FileBadge(fileName = name)
                        Text(text = name, color = LocalIntelliJColors.current.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        GallerySection("Popup chrome · popupChrome + PopupListRow") {
            var selected by remember { mutableStateOf(1) }
            Column(
                modifier = Modifier.width(Dimensions.popupCompletionWidth.dp).popupChrome(),
            ) {
                listOf("Open recent project", "Reload from disk", "Reveal in Finder").forEachIndexed { index, label ->
                    PopupListRow(selected = index == selected, onClick = { selected = index }) {
                        Text(text = label, color = LocalIntelliJColors.current.textPrimary, fontSize = 13.sp)
                    }
                }
            }
        }

        GallerySection("Author avatars · Avatar") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp)) {
                listOf("Aleksandr K.", "Marta V.", "you", "Jane Doe").forEach { Avatar(author = it, size = 24.dp) }
            }
        }

        GallerySection("Color palette · IntelliJColors") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md.dp),
            ) {
                paletteSwatches().forEach { SwatchCard(it) }
            }
        }

        GallerySection("Spacing scale · Spacing") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
                SPACING_STEPS.forEach { (name, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp),
                    ) {
                        Text(
                            text = "$name ($value)",
                            color = LocalIntelliJColors.current.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.width(80.dp),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .size(
                                        width = (value * 6).dp,
                                        height = 12.dp,
                                    ).background(LocalIntelliJColors.current.accent),
                        )
                    }
                }
            }
        }

        GallerySection("Status bar · IntelliJStatusBar") {
            IntelliJStatusBar(
                gitBranch = "feature/welcome-screen",
                isDirty = true,
                errorCount = 0,
                warningCount = 2,
                lineInfo = "24:38",
                indentInfo = "4 spaces",
                languageInfo = "Kotlin",
                buildStatus = BuildStatus(success = true),
            )
        }
    }
}

@Composable
private fun GallerySection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontFamily = JetaProgFonts.codeFont,
        )
        content()
    }
}

@Composable
private fun SwatchCard(swatch: Swatch) {
    Column(
        modifier =
            Modifier
                .width(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LocalIntelliJColors.current.surface),
    ) {
        Box(modifier = Modifier.fillMaxWidth().size(height = 48.dp, width = 150.dp).background(swatch.color))
        Text(
            text = swatch.name,
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 11.sp,
            fontFamily = JetaProgFonts.codeFont,
            modifier = Modifier.padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        )
    }
}
