package su.kidoz.jetaprog.app.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.components.BuildStatus
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJStatusBar
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * IDE-native `@Preview` catalog. Open this file in the IDE and use the preview
 * gutter to render components without launching the app — a lightweight
 * complement to the runnable [ComponentGallery].
 */
@Preview
@Composable
private fun GalleryPreview() {
    JetaProgTheme { ComponentGallery() }
}

@Preview
@Composable
private fun ButtonsPreview() {
    JetaProgTheme {
        Column(
            modifier = Modifier.background(IntelliJColors.background).padding(Spacing.lg.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            IntelliJButton(text = "Primary", onClick = {}, style = ButtonStyle.PRIMARY, icon = Icons.Default.Add)
            IntelliJButton(text = "Secondary", onClick = {}, style = ButtonStyle.SECONDARY)
            IntelliJButton(text = "Danger", onClick = {}, style = ButtonStyle.DANGER)
            IntelliJButton(text = "Disabled", onClick = {}, enabled = false)
        }
    }
}

@Preview
@Composable
private fun TextFieldsPreview() {
    var query by remember { mutableStateOf("") }
    JetaProgTheme {
        Column(
            modifier = Modifier.background(IntelliJColors.background).padding(Spacing.lg.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            IntelliJTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search files",
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search files",
                        tint = IntelliJColors.textSecondary,
                        modifier = Modifier.size(Dimensions.iconMd.dp),
                    )
                },
            )
        }
    }
}

@Preview
@Composable
private fun StatusBarPreview() {
    JetaProgTheme {
        IntelliJStatusBar(
            gitBranch = "main",
            isDirty = true,
            warningCount = 2,
            errorCount = 1,
            lineInfo = "12:4",
            indentInfo = "4 spaces",
            languageInfo = "Kotlin",
            buildStatus = BuildStatus(success = true),
        )
    }
}
