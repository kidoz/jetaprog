package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.dialogs.DialogContainer
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** Inspects the current draft; queued messages retain the context captured when queued. */
@Composable
internal fun AgentContextInspector(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { dispatch(AgentIntent.HideContext) },
        properties = PopupProperties(focusable = true),
    ) {
        val focus = remember { FocusRequester() }
        Column(Modifier.focusRequester(focus)) {
            AgentContextInspectorContent(state, dispatch)
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

/** Shared inspector body for the production popup, component gallery, and previews. */
@Composable
internal fun AgentContextInspectorContent(
    state: AgentUiState,
    dispatch: (AgentIntent) -> Unit,
) {
    DialogContainer(
        Modifier.widthIn(max = Dimensions.popupListWidth.dp).onPreviewKeyEvent {
            if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                dispatch(AgentIntent.HideContext)
                true
            } else {
                false
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
            ContextText("Context for next message", heading = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
                IntelliJButton(
                    "Refresh",
                    onClick = { dispatch(AgentIntent.RefreshContext) },
                    modifier = contextActionFocus(),
                )
                IntelliJButton(
                    "Close",
                    onClick = { dispatch(AgentIntent.HideContext) },
                    modifier = contextActionFocus(),
                )
            }
            val scroll = rememberScrollState()
            Box(Modifier.heightIn(max = Dimensions.popupListMaxHeight.dp)) {
                Column(
                    Modifier.verticalScroll(scroll).padding(end = Spacing.md.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
                ) {
                    ContextText("Checkout", heading = true)
                    ContextText(state.workspacePath.ifBlank { "Workspace unavailable" })
                    ContextText("This is the current draft. Already queued messages keep their captured attachments.")
                    HorizontalDivider(color = LocalIntelliJColors.current.divider)
                    ContextText("Attachments (${state.composerAttachments.size})", heading = true)
                    if (state.composerAttachments.isEmpty()) ContextText("No files or selections attached.")
                    for (attachment in state.composerAttachments) {
                        ContextAttachment(attachment, dispatch)
                    }
                    HorizontalDivider(color = LocalIntelliJColors.current.divider)
                    InstructionDetails(state.instructionPreview)
                    HorizontalDivider(color = LocalIntelliJColors.current.divider)
                    ContextText("Conversation", heading = true)
                    ContextText(
                        when (state.restoration) {
                            is SessionRestoration.TranscriptOnly -> {
                                "Saved transcript only. Sending starts a fresh agent conversation."
                            }

                            SessionRestoration.Loading -> {
                                "Restoring the conversation. Sending is unavailable until restoration finishes."
                            }

                            else -> {
                                if (state.turns.isEmpty()) {
                                    "New conversation."
                                } else {
                                    "Continues the current agent conversation."
                                }
                            }
                        },
                    )
                    ContextText(
                        "The agent manages conversation history and may load additional context. That context is not shown here.",
                    )
                    OutgoingTextPreview(state)
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
private fun ContextAttachment(
    attachment: ComposerAttachment,
    dispatch: (AgentIntent) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(start = Spacing.sm.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Column(Modifier.weight(1f)) {
                ContextText(chipLabel(attachment), heading = true)
            }
            IntelliJButton(
                "Remove",
                onClick = { dispatch(AgentIntent.RemoveAttachment(attachment.path, attachment.excerpt != null)) },
                modifier =
                    contextActionFocus().semantics {
                        contentDescription =
                            "Remove ${chipLabel(attachment)} from context"
                    },
            )
        }
        ContextText(attachment.path)
        if (attachment.excerpt == null) {
            ContextText("File reference only. File contents are not embedded in the prompt.")
        } else {
            ContextText("Selected text snapshot")
            ExpandableContextText(attachment.excerpt, "selection in ${attachment.path}")
        }
    }
}

@Composable
private fun InstructionDetails(preview: ProjectInstructionPreview) {
    ContextText("Project instructions · AGENTS.md", heading = true)
    when {
        preview.loading -> {
            ContextText("Reading root instructions…")
        }

        preview.text != null -> {
            ContextText(
                "${preview.text.length} characters included" +
                    if (preview.truncated) " · truncated to $AGENTS_MAX_CHARS" else "",
            )
            ExpandableContextText(preview.text, "project instructions")
        }

        else -> {
            ContextText(preview.notice ?: "No instruction snapshot available. Refresh to inspect.")
        }
    }
    ContextText(
        "JetaProg adds root instructions only; nested files and imports are not expanded. " +
            "Instructions are reread when sending.",
    )
}

@Composable
private fun OutgoingTextPreview(state: AgentUiState) {
    val prompt = state.composerInput.trim()
    val localCommand =
        parseGoalCommand(prompt) != null || parseReviewCommand(prompt) || parseWorktreeCommand(prompt) ||
            parseBringCommand(prompt)
    when {
        prompt.isBlank() -> {
            ContextText("Write a message to preview its outgoing text.")
        }

        localCommand -> {
            ContextText(
                "This is a JetaProg command. It may start a separate workflow; its generated prompts are not previewed here.",
            )
        }

        !state.instructionPreview.loading -> {
            val outgoing =
                promptWithInstructions(
                    state.instructionPreview.text,
                    promptWithContext(prompt, state.composerAttachments),
                )
            ExpandableContextText(outgoing, "outgoing text")
        }
    }
}

@Composable
private fun ExpandableContextText(
    text: String,
    label: String,
) {
    var expanded by remember(label) { mutableStateOf(false) }
    IntelliJButton(
        text = if (expanded) "Hide $label" else "Show $label",
        onClick = { expanded = !expanded },
        modifier = contextActionFocus(),
    )
    if (expanded) {
        SelectionContainer {
            Text(
                text,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Preview: $label" },
                color = LocalIntelliJColors.current.textPrimary,
                fontFamily = JetaProgFonts.codeFont,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ContextText(
    text: String,
    heading: Boolean = false,
) {
    Text(
        text,
        color = if (heading) LocalIntelliJColors.current.textPrimary else LocalIntelliJColors.current.textSecondary,
        fontFamily = JetaProgFonts.codeFont,
        fontSize = 12.sp,
        fontWeight = if (heading) FontWeight.Medium else FontWeight.Normal,
    )
}

@Composable
private fun contextActionFocus(): Modifier = Modifier.agentFocusRing(RoundedCornerShape(Dimensions.cornerRadius.dp))
