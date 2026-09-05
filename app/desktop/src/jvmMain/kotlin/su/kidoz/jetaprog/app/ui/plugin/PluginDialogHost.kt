package su.kidoz.jetaprog.app.ui.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.plugin.PluginDialogRequest
import su.kidoz.jetaprog.app.plugin.PluginDialogRequests
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJTextField
import su.kidoz.jetaprog.app.ui.dialogs.IntelliJDialog
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Renders the current plugin modal request (input box or quick pick) from
 * [requestQueue] as a dialog and completes it with the user's answer.
 *
 * Place it once in the main shell, next to the notification overlay.
 */
@Composable
public fun PluginDialogHost(requestQueue: PluginDialogRequests) {
    val request by requestQueue.requests.collectAsState(initial = null)
    when (val current = request) {
        is PluginDialogRequest.Input -> PluginInputDialog(current)
        is PluginDialogRequest.QuickPick -> PluginQuickPickDialog(current)
        null -> Unit
    }
}

@Composable
private fun PluginInputDialog(request: PluginDialogRequest.Input) {
    var text by remember(request) { mutableStateOf(request.value.orEmpty()) }

    IntelliJDialog(
        onDismissRequest = { request.completion.cancel() },
        minWidth = Dimensions.dialogMinWidth.dp,
        maxWidth = Dimensions.dialogMinWidth.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
        ) {
            Text(
                text = request.title ?: request.prompt ?: "Input",
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            IntelliJTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = request.placeholder.orEmpty(),
                visualTransformation =
                    if (request.password) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp, Alignment.End),
            ) {
                IntelliJButton(text = "Cancel", onClick = { request.completion.cancel() })
                IntelliJButton(text = "OK", onClick = { request.completion.complete(text) })
            }
        }
    }
}

@Composable
private fun PluginQuickPickDialog(request: PluginDialogRequest.QuickPick) {
    val checked = remember(request) { mutableStateMapOf<Int, Boolean>() }

    IntelliJDialog(
        onDismissRequest = { request.completion.cancel() },
        minWidth = Dimensions.dialogMinWidth.dp,
        maxWidth = Dimensions.dialogMinWidth.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
        ) {
            Text(
                text = request.title ?: "Select an item",
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs.dp),
            ) {
                itemsIndexed(request.items) { index, item ->
                    val isSelected =
                        if (request.multiSelect) {
                            checked[index] == true
                        } else {
                            checked.isEmpty() && item.picked
                        }
                    QuickPickRow(
                        label = item.label,
                        description = item.description,
                        isSelected = isSelected,
                        onClick = {
                            if (request.multiSelect) {
                                checked[index] = !(checked[index] == true)
                            } else {
                                request.completion.complete(listOf(item))
                            }
                        },
                    )
                }
            }
            if (request.multiSelect) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp, Alignment.End),
                ) {
                    IntelliJButton(text = "Cancel", onClick = { request.completion.cancel() })
                    IntelliJButton(
                        text = "OK",
                        onClick = {
                            request.completion.complete(
                                request.items.filterIndexed {
                                    index,
                                    _,
                                    ->
                                    checked[index] == true
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPickRow(
    label: String,
    description: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor =
        when {
            isSelected -> LocalIntelliJColors.current.accentSubtle
            isHovered -> LocalIntelliJColors.current.surfaceHover
            else -> Color.Transparent
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm.dp, vertical = Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.codeFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        description?.let {
            Text(
                text = " — $it",
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 12.sp,
                fontFamily = JetaProgFonts.codeFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
