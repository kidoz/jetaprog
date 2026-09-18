package su.kidoz.jetaprog.app.ui.dialogs.filepicker

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJCheckbox
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.dialogs.DialogContainer
import su.kidoz.jetaprog.app.ui.dialogs.DialogOverlay
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Hosts the app-wide file picker: collects [viewModel] state and forwards
 * intents. The picked path leaves through [FilePickerViewModel.effects].
 */
@Composable
public fun FilePickerHost(
    viewModel: FilePickerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    FilePickerDialog(state = state, onIntent = viewModel::dispatch, modifier = modifier)
}

/**
 * The IDE's own file picker, drawn in the IDE theme on every platform: a
 * places rail, an editable path, the directory listing, and - in save mode -
 * a file name field. Enter runs the primary action; Escape backs out of the
 * innermost pending step, then closes the dialog.
 */
@Composable
public fun FilePickerDialog(
    state: FilePickerState,
    onIntent: (FilePickerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val request = state.request ?: return
    val onEscape = {
        when {
            state.overwriteTarget != null -> onIntent(FilePickerIntent.CancelOverwrite)
            state.newFolderName != null -> onIntent(FilePickerIntent.CancelNewFolder)
            else -> onIntent(FilePickerIntent.Dismiss)
        }
    }

    DialogOverlay(isVisible = state.isVisible, onDismiss = { onIntent(FilePickerIntent.Dismiss) }) {
        DialogContainer(
            modifier =
                modifier
                    .width(Dimensions.dialogFilePickerWidth.dp)
                    .onPreviewKeyEvent { event ->
                        when {
                            event.type != KeyEventType.KeyDown -> {
                                false
                            }

                            event.key == Key.Enter -> {
                                onIntent(FilePickerIntent.Confirm)
                                true
                            }

                            event.key == Key.Escape -> {
                                onEscape()
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
        ) {
            Text(
                text = request.title,
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = FONT_TITLE.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(Spacing.md.dp))

            PathBar(state = state, onIntent = onIntent)
            Spacer(modifier = Modifier.height(Spacing.sm.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(Dimensions.dialogFilePickerListHeight.dp)
                        .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                        .border(
                            width = 1.dp,
                            color = LocalIntelliJColors.current.border,
                            shape = RoundedCornerShape(Dimensions.cornerRadius.dp),
                        ),
            ) {
                PlacesRail(state = state, onIntent = onIntent)
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(LocalIntelliJColors.current.border),
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    state.newFolderName?.let { name -> NewFolderRow(name = name, onIntent = onIntent) }
                    EntryList(state = state, onIntent = onIntent, modifier = Modifier.weight(1f))
                }
            }

            if (state.mode == FilePickerMode.SAVE_FILE) {
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                FileNameField(state = state, onIntent = onIntent)
            }

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(Spacing.sm.dp))
                Text(text = error, color = LocalIntelliJColors.current.error, fontSize = FONT_SMALL.sp)
            }
            state.overwriteTarget?.let { target -> OverwritePrompt(target = target, onIntent = onIntent) }

            Spacer(modifier = Modifier.height(Spacing.md.dp))
            HorizontalDivider(color = LocalIntelliJColors.current.divider)
            Spacer(modifier = Modifier.height(Spacing.md.dp))

            Footer(state = state, confirmLabel = request.confirmLabel, onIntent = onIntent)
        }
    }
}

/** Up button, editable path, and New Folder. The path takes focus when the dialog opens. */
@Composable
private fun PathBar(
    state: FilePickerState,
    onIntent: (FilePickerIntent) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Save mode focuses the file name instead; see FileNameField.
    if (state.mode != FilePickerMode.SAVE_FILE) {
        LaunchedEffect(state.request) { focusRequester.requestFocus() }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        IntelliJButton(
            text = "Up",
            onClick = { onIntent(FilePickerIntent.NavigateUp) },
            icon = Icons.Default.ArrowUpward,
        )
        IntelliJTextField(
            value = state.pathInput,
            onValueChange = { onIntent(FilePickerIntent.PathInputChanged(it)) },
            placeholder = "Type a path and press Enter",
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        IntelliJButton(
            text = "New Folder",
            onClick = { onIntent(FilePickerIntent.StartNewFolder) },
            icon = Icons.Default.CreateNewFolder,
            enabled = state.newFolderName == null,
        )
    }
}

@Composable
private fun PlacesRail(
    state: FilePickerState,
    onIntent: (FilePickerIntent) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(Dimensions.dialogFilePickerPlacesWidth.dp)
                .fillMaxHeight()
                .background(LocalIntelliJColors.current.backgroundDarker)
                .padding(vertical = Spacing.xs.dp),
    ) {
        state.places.forEach { place ->
            PickerRow(
                icon = placeIcon(place),
                iconTint = LocalIntelliJColors.current.iconDefault,
                label = place.label,
                isSelected = place.path == state.currentDirectory,
                onClick = { onIntent(FilePickerIntent.NavigateTo(place.path)) },
            )
        }
    }
}

private fun placeIcon(place: FilePickerPlace): ImageVector =
    when (place.label) {
        "Home" -> Icons.Default.Home
        "Computer" -> Icons.Default.Storage
        else -> Icons.Default.Folder
    }

@Composable
private fun EntryList(
    state: FilePickerState,
    onIntent: (FilePickerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = state.entries
    val listState = rememberLazyListState()
    // A second click on the same row within the window counts as a double-click.
    // Handled here rather than with a double-tap detector, which would delay
    // every single-click selection by the double-tap timeout.
    var lastClick by remember(state.currentDirectory) { mutableStateOf<Pair<String, Long>?>(null) }

    LaunchedEffect(state.currentDirectory) { listState.scrollToItem(0) }

    Box(modifier = modifier.fillMaxWidth().background(LocalIntelliJColors.current.treeBackground)) {
        if (entries.isEmpty()) {
            Text(
                text = emptyListMessage(state),
                color = LocalIntelliJColors.current.textMuted,
                fontSize = FONT_BODY.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(vertical = Spacing.xs.dp)) {
            items(entries, key = { it.path }) { entry ->
                PickerRow(
                    icon = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                    iconTint =
                        if (entry.isDirectory) {
                            LocalIntelliJColors.current.iconFolder
                        } else {
                            LocalIntelliJColors.current.iconFile
                        },
                    label = entry.name,
                    isSelected = entry.path == state.selectedPath,
                    isDimmed = entry.isHidden,
                    detail = if (entry.isFile) formatSize(entry.size) else null,
                    onClick = {
                        val now = System.currentTimeMillis()
                        val previous = lastClick
                        if (previous != null && previous.first == entry.path &&
                            now - previous.second <= DOUBLE_CLICK_MILLIS
                        ) {
                            lastClick = null
                            onIntent(FilePickerIntent.ActivateEntry(entry.path))
                        } else {
                            lastClick = entry.path to now
                            onIntent(FilePickerIntent.SelectEntry(entry.path))
                        }
                    },
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

private fun emptyListMessage(state: FilePickerState): String =
    when {
        state.isLoading -> "Loading…"
        state.mode == FilePickerMode.OPEN_DIRECTORY -> "No subfolders."
        else -> "This folder is empty."
    }

/** One row of the places rail or the listing. */
@Composable
private fun PickerRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDimmed: Boolean = false,
    detail: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val background =
        when {
            isSelected -> LocalIntelliJColors.current.treeSelectionBackground
            isHovered -> LocalIntelliJColors.current.treeHoverBackground
            else -> Color.Transparent
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.popupRowHeight.dp)
                .background(background)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(Dimensions.iconMd.dp),
        )
        Text(
            text = label,
            color =
                if (isDimmed) {
                    LocalIntelliJColors.current.treeForegroundIgnored
                } else {
                    LocalIntelliJColors.current.treeForeground
                },
            fontSize = FONT_BODY.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        detail?.let {
            Text(text = it, color = LocalIntelliJColors.current.textMuted, fontSize = FONT_SMALL.sp, maxLines = 1)
        }
    }
}

/** Inline row for naming a new folder; Enter creates it, Escape closes the row. */
@Composable
private fun NewFolderRow(
    name: String,
    onIntent: (FilePickerIntent) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(LocalIntelliJColors.current.surfaceElevated)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        IntelliJTextField(
            value = name,
            onValueChange = { onIntent(FilePickerIntent.NewFolderNameChanged(it)) },
            placeholder = "New folder name",
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        IntelliJButton(
            text = "Create",
            onClick = { onIntent(FilePickerIntent.Confirm) },
            style = ButtonStyle.PRIMARY,
            enabled = name.isNotBlank(),
        )
        IntelliJButton(text = "Discard", onClick = { onIntent(FilePickerIntent.CancelNewFolder) })
    }
}

@Composable
private fun FileNameField(
    state: FilePickerState,
    onIntent: (FilePickerIntent) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.request) { focusRequester.requestFocus() }

    IntelliJTextField(
        value = state.fileName,
        onValueChange = { onIntent(FilePickerIntent.FileNameChanged(it)) },
        label = "File name:",
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

@Composable
private fun OverwritePrompt(
    target: String,
    onIntent: (FilePickerIntent) -> Unit,
) {
    Spacer(modifier = Modifier.height(Spacing.sm.dp))
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .background(LocalIntelliJColors.current.warningMuted)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Text(
            text = "“${target.substringAfterLast('/').substringAfterLast('\\')}” already exists. Replace it?",
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = FONT_BODY.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IntelliJButton(
            text = "Replace",
            onClick = { onIntent(FilePickerIntent.ConfirmOverwrite) },
            style = ButtonStyle.DANGER,
        )
        IntelliJButton(text = "Keep", onClick = { onIntent(FilePickerIntent.CancelOverwrite) })
    }
}

@Composable
private fun Footer(
    state: FilePickerState,
    confirmLabel: String,
    onIntent: (FilePickerIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IntelliJCheckbox(
            checked = state.showHidden,
            onCheckedChange = { onIntent(FilePickerIntent.SetShowHidden(it)) },
            label = "Show hidden files",
        )
        Spacer(modifier = Modifier.weight(1f))
        IntelliJButton(text = "Cancel", onClick = { onIntent(FilePickerIntent.Dismiss) })
        Spacer(modifier = Modifier.width(Spacing.sm.dp))
        IntelliJButton(
            text = confirmLabel,
            onClick = { onIntent(FilePickerIntent.Confirm) },
            style = ButtonStyle.PRIMARY,
            enabled = state.canConfirm && state.newFolderName == null,
        )
    }
}

private fun formatSize(bytes: Long): String {
    var value = bytes.toDouble()
    var unit = 0
    while (value >= SIZE_STEP && unit < SIZE_UNITS.lastIndex) {
        value /= SIZE_STEP
        unit++
    }
    return if (unit == 0) "$bytes B" else "%.1f %s".format(value, SIZE_UNITS[unit])
}

private const val FONT_TITLE = 18
private const val FONT_BODY = 13
private const val FONT_SMALL = 12
private const val DOUBLE_CLICK_MILLIS = 400L
private const val SIZE_STEP = 1024.0
private val SIZE_UNITS = listOf("B", "KB", "MB", "GB", "TB")
