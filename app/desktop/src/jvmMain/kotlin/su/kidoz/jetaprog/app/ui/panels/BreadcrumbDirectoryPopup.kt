package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.PopupChromeMenu
import su.kidoz.jetaprog.app.ui.components.PopupListRow
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.platform.filesystem.FileEntry
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * Directory listing popup opened from a breadcrumbs DIRECTORY/PROJECT segment
 * (IntelliJ shows the same dropdown): lists the directory's entries; files open
 * on click, subdirectories drill in.
 */
@Composable
public fun BreadcrumbDirectoryPopup(
    directory: String,
    fileSystem: FileSystem?,
    onOpenFile: (String) -> Unit,
    onOpenDirectory: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var entries by remember(directory) { mutableStateOf<List<FileEntry>>(emptyList()) }
    var errorText by remember(directory) { mutableStateOf<String?>(null) }

    LaunchedEffect(directory) {
        val fs = fileSystem
        if (fs == null) {
            errorText = "File system unavailable"
            return@LaunchedEffect
        }
        fs
            .listDirectory(directory)
            .onSuccess { list ->
                errorText = null
                entries =
                    list
                        .filter { !it.isHidden }
                        .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name })
            }.onFailure { error -> errorText = error.message ?: "Could not list directory" }
    }

    val density = LocalDensity.current
    PopupChromeMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = modifier,
        alignment = androidx.compose.ui.Alignment.TopStart,
        offsetY = with(density) { Dimensions.breadcrumbsHeight.dp.roundToPx() },
    ) {
        if (errorText != null) {
            Text(
                text = errorText.orEmpty(),
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
            )
        } else if (entries.isEmpty()) {
            Text(
                text = "Empty folder",
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = Spacing.md.dp, vertical = Spacing.xs.dp),
            )
        } else {
            for (entry in entries) {
                PopupListRow(
                    selected = false,
                    height = Dimensions.popupRowHeightCompact.dp,
                    onClick = {
                        if (entry.isDirectory) {
                            onOpenDirectory(entry.path)
                        } else {
                            onOpenFile(entry.path)
                        }
                    },
                ) {
                    val entryIcon =
                        if (entry.isDirectory) {
                            Icons.Filled.Folder
                        } else {
                            Icons.AutoMirrored.Filled.InsertDriveFile
                        }
                    Icon(
                        imageVector = entryIcon,
                        contentDescription = null,
                        tint =
                            if (entry.isDirectory) {
                                LocalIntelliJColors.current.iconFolder
                            } else {
                                LocalIntelliJColors.current.iconDefault
                            },
                        modifier = Modifier.size(Dimensions.iconSm.dp),
                    )
                    Spacer(Modifier.size(Spacing.xs.dp))
                    Text(
                        text = entry.name,
                        color = LocalIntelliJColors.current.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
