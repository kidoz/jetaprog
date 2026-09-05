package su.kidoz.jetaprog.app.ui.dialogs.settings.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.keymap.ACTION_DISPLAY_NAMES
import su.kidoz.jetaprog.app.keymap.ASSIGNABLE_KEYS
import su.kidoz.jetaprog.app.keymap.KeyboardShortcut
import su.kidoz.jetaprog.app.keymap.toSpec
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.settings.model.KeymapSettings
import su.kidoz.jetaprog.settings.model.ShortcutSpec

/**
 * Panel for viewing and customizing keyboard shortcuts.
 *
 * Each action shows its effective shortcut (custom override or default).
 * "Modify" records the next key chord; "Reset" drops a custom override.
 */
@Composable
public fun KeymapPanel(
    settings: KeymapSettings,
    shortcuts: Map<String, KeyboardShortcut>,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var recordingAction by remember { mutableStateOf<String?>(null) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(Spacing.sm.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Text(
            text =
                "Press \"Modify\" on an action, then press the new key combination. " +
                    "Custom bindings override the defaults shown here.",
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 11.sp,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs.dp),
        ) {
            items(shortcuts.entries.toList(), key = { it.key }) { (action, shortcut) ->
                val isRecording = recordingAction == action
                KeymapActionRow(
                    actionName = ACTION_DISPLAY_NAMES[action] ?: action,
                    shortcutText =
                        when {
                            isRecording -> {
                                "Press keys…"
                            }

                            else -> {
                                shortcuts[action]?.toDisplayString(isMac = isMacOperatingSystem())
                                    ?: "—"
                            }
                        },
                    isCustom = settings.customShortcuts.containsKey(action),
                    isRecording = isRecording,
                    onModifyClick = {
                        recordingAction = if (isRecording) null else action
                    },
                    onResetClick = {
                        recordingAction = null
                        onIntent(SettingsIntent.ResetShortcut(action))
                    },
                    onRecorded = { event ->
                        recordingAction = null
                        shortcutFromEvent(event)?.let { spec ->
                            onIntent(SettingsIntent.SetShortcut(action, spec))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun KeymapActionRow(
    actionName: String,
    shortcutText: String,
    isCustom: Boolean,
    isRecording: Boolean,
    onModifyClick: () -> Unit,
    onResetClick: () -> Unit,
    onRecorded: (KeyEvent) -> Unit,
) {
    // During recording the row owns the keyboard: Escape cancels, any other
    // KeyDown is handed to the recorder and consumed.
    val recordingKeyHandler: (KeyEvent) -> Boolean =
        { event ->
            when {
                event.type != KeyEventType.KeyDown -> {
                    false
                }

                event.key == Key.Escape -> {
                    onModifyClick()
                    true
                }

                else -> {
                    onRecorded(event)
                    true
                }
            }
        }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(Dimensions.cornerRadiusSmall.dp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    when {
                        isRecording -> LocalIntelliJColors.current.accentSubtle
                        isHovered -> LocalIntelliJColors.current.surfaceHover
                        else -> Color.Transparent
                    },
                ).hoverable(interactionSource)
                .then(if (isRecording) Modifier.onKeyEvent(recordingKeyHandler) else Modifier)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Text(
            text = actionName,
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isCustom) {
            Text(
                text = "custom",
                color = LocalIntelliJColors.current.textLink,
                fontSize = 10.sp,
            )
        }
        Text(
            text = shortcutText,
            color =
                if (isRecording) {
                    LocalIntelliJColors.current.textLink
                } else {
                    LocalIntelliJColors.current.textSecondary
                },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = JetaProgFonts.codeFont,
            modifier =
                Modifier
                    .border(
                        Dimensions.splitterThickness.dp,
                        LocalIntelliJColors.current.inputBorder,
                        shape,
                    ).clip(shape)
                    .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xxs.dp),
        )
        IntelliJButton(text = if (isRecording) "Cancel" else "Modify", onClick = onModifyClick)
        if (isCustom) {
            IntelliJButton(text = "Reset", onClick = onResetClick)
        }
    }
}

/** True when running on macOS, used to render ⌘-style shortcut text. */
private fun isMacOperatingSystem(): Boolean = "Mac" in System.getProperty("os.name", "")

/**
 * Converts a recorded key event into a persisted shortcut spec, or null when
 * the pressed key is not assignable (modifier-only chords, unknown keys).
 */
private fun shortcutFromEvent(event: KeyEvent): ShortcutSpec? {
    val keyCode = event.key.keyCode
    if (ASSIGNABLE_KEYS.none { it.keyCode == keyCode }) return null
    return ShortcutSpec(
        keyCode = keyCode,
        ctrl = event.isCtrlPressed,
        shift = event.isShiftPressed,
        alt = event.isAltPressed,
        meta = event.isMetaPressed,
    )
}
