package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import su.kidoz.jetaprog.acp.protocol.SessionConfigChoice
import su.kidoz.jetaprog.acp.protocol.SessionConfigOption
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJCheckbox
import su.kidoz.jetaprog.app.ui.components.PopupChromeMenu
import su.kidoz.jetaprog.app.ui.components.PopupListRow
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** One agent-advertised select value, with its optional group label. */
@Immutable
public data class AgentConfigValue(
    /** Wire value. */
    val value: String,
    /** Visible label. */
    val name: String,
    /** Optional explanation. */
    val description: String? = null,
    /** Optional grouping label. */
    val group: String? = null,
)

/** A select or boolean session configuration control. */
@Immutable
public data class AgentConfigOption(
    /** Wire identifier. */
    val id: String,
    /** Agent-provided label. */
    val name: String,
    /** Confirmed select value; empty for boolean controls. */
    val currentValue: String,
    /** Choices in agent-provided order. */
    val values: List<AgentConfigValue>,
    /** Optional explanation. */
    val description: String? = null,
    /** Optional semantic category. */
    val category: String? = null,
    /** Confirmed boolean value; null identifies a select control. */
    val booleanValue: Boolean? = null,
)

private val configJson = Json { ignoreUnknownKeys = true }

/** Unsupported types are ignored without losing the rest of the configuration. */
internal fun SessionConfigOption.toAgentConfigOption(): AgentConfigOption? {
    val current = currentValue as? JsonPrimitive ?: return null
    if (type == "boolean" && !current.isString) {
        val checked = current.booleanOrNull ?: return null
        return AgentConfigOption(id, name, "", emptyList(), description, category, booleanValue = checked)
    }
    if (type != "select" || !current.isString) return null
    val choices =
        runCatching { configJson.decodeFromJsonElement<List<SessionConfigChoice>>(options) }.getOrNull() ?: return null
    val values =
        choices.flatMap { item ->
            if (item.group != null) {
                item.options.mapNotNull { choice ->
                    choice.value?.let { AgentConfigValue(it, choice.name, choice.description, item.name) }
                }
            } else {
                listOfNotNull(item.value?.let { AgentConfigValue(it, item.name, item.description) })
            }
        }
    return AgentConfigOption(id, name, current.content, values, description, category)
}

/** Fixed-size entry point so every advertised setting remains reachable in a narrow dock. */
@Composable
internal fun AgentConfigMenu(
    options: List<AgentConfigOption>,
    enabled: Boolean,
    dispatch: (AgentIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var anchorHeight by remember { mutableStateOf(0) }
    val selected = options.firstOrNull { it.id == selectedId }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    Box(modifier.onSizeChanged { anchorHeight = it.height }) {
        IconButton(
            onClick = {
                selectedId = null
                open = true
            },
            enabled = enabled,
            modifier =
                Modifier
                    .size(Dimensions.inputHeight.dp)
                    .agentFocusRing(RoundedCornerShape(Dimensions.cornerRadius.dp)),
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = "Session settings",
                tint = LocalIntelliJColors.current.textSecondary,
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )
        }
        PopupChromeMenu(expanded = open && enabled, onDismissRequest = { open = false }, offsetY = anchorHeight) {
            if (selected == null) {
                Text(
                    "Session settings",
                    color = LocalIntelliJColors.current.textSecondary,
                    modifier = Modifier.padding(Spacing.sm.dp),
                )
                options.forEach { option ->
                    val current =
                        option.booleanValue?.let { if (it) "On" else "Off" }
                            ?: option.values.firstOrNull { it.value == option.currentValue }?.name
                            ?: option.currentValue
                    PopupListRow(
                        selected = false,
                        height = (Dimensions.popupRowHeight + Dimensions.popupRowHeightCompact).dp,
                        modifier = Modifier.semantics { contentDescription = "${option.name}: $current" },
                        onClick = { selectedId = option.id },
                    ) {
                        Column {
                            Text(option.name, color = LocalIntelliJColors.current.textPrimary, fontSize = 12.sp)
                            Text(
                                current,
                                color = LocalIntelliJColors.current.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                IntelliJButton("Back to settings", onClick = { selectedId = null })
                Text(
                    selected.name,
                    color = LocalIntelliJColors.current.textPrimary,
                    modifier = Modifier.padding(Spacing.sm.dp),
                )
                if (selected.booleanValue != null) {
                    selected.description?.let {
                        Text(
                            it,
                            color = LocalIntelliJColors.current.textSecondary,
                            modifier = Modifier.padding(Spacing.sm.dp),
                        )
                    }
                    IntelliJCheckbox(
                        checked = selected.booleanValue,
                        label = "Enabled",
                        onCheckedChange = { value ->
                            dispatch(AgentIntent.SetBooleanConfigOption(selected.id, value))
                            open = false
                        },
                        modifier =
                            Modifier
                                .padding(Spacing.sm.dp)
                                .agentFocusRing(RoundedCornerShape(Dimensions.cornerRadius.dp))
                                .semantics {
                                    contentDescription = selected.name
                                    role = Role.Checkbox
                                    toggleableState = ToggleableState(selected.booleanValue)
                                },
                    )
                } else {
                    AgentConfigChoices(selected) { value ->
                        dispatch(AgentIntent.SetConfigOption(selected.id, value))
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentConfigChoices(
    option: AgentConfigOption,
    select: (String) -> Unit,
) {
    option.description?.let {
        Text(it, color = LocalIntelliJColors.current.textSecondary, modifier = Modifier.padding(Spacing.sm.dp))
    }
    option.values.forEachIndexed { index, choice ->
        if (choice.group != null && (index == 0 || option.values[index - 1].group != choice.group)) {
            Text(
                choice.group,
                color = LocalIntelliJColors.current.textSecondary,
                modifier = Modifier.padding(Spacing.sm.dp),
            )
        }
        val rowHeight =
            Dimensions.popupRowHeight +
                if (choice.description != null) Dimensions.popupRowHeightCompact else 0
        PopupListRow(selected = option.currentValue == choice.value, height = rowHeight.dp, onClick = {
            select(choice.value)
        }) {
            Column {
                Text(
                    choice.name,
                    color = LocalIntelliJColors.current.textPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                choice.description?.let {
                    Text(
                        it,
                        color = LocalIntelliJColors.current.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
