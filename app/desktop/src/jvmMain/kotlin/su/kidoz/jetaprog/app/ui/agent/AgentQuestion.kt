package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/** One pending structured question, independently identified from tool approvals. */
@Immutable
public data class AgentQuestion(
    val id: String,
    val message: String,
    val fields: List<AgentQuestionField>,
    val answers: Map<String, List<String>> = emptyMap(),
    val error: String? = null,
)

/** A primitive or choice field; optional fields may be left unanswered. */
@Immutable
public data class AgentQuestionField(
    val id: String,
    val title: String,
    val description: String?,
    val type: String,
    val required: Boolean,
    val choices: Map<String, String> = emptyMap(),
)

/** A single actionable question next to the composer, never duplicated in the transcript. */
@Composable
internal fun AgentQuestionSection(
    question: AgentQuestion,
    agentName: String,
    dispatch: (AgentIntent) -> Unit,
    maxHeight: Dp,
) {
    val colors = LocalIntelliJColors.current
    CompositionLocalProvider(LocalContentColor provides LocalIntelliJColors.current.textPrimary) {
        Column(
            Modifier.fillMaxWidth().background(colors.toolCardBackground).padding(Spacing.sm.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Text("$agentName needs your input", style = MaterialTheme.typography.labelLarge)
            Column(
                Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            ) {
                Text(question.message, style = MaterialTheme.typography.bodySmall)
                Text("Only provide non-sensitive information.", style = MaterialTheme.typography.bodySmall)
                question.fields.forEach { field ->
                    Text(
                        "${field.title}${if (field.required) " (required)" else " (optional)"}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    field.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    val values = question.answers[field.id].orEmpty()
                    if (field.choices.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp)) {
                            field.choices.forEach { (value, label) ->
                                val chosen = value in values
                                IntelliJButton(
                                    label,
                                    modifier = Modifier.semantics { selected = chosen },
                                    style =
                                        if (chosen) {
                                            ButtonStyle.PRIMARY
                                        } else {
                                            ButtonStyle.SECONDARY
                                        },
                                    onClick = {
                                        val selected =
                                            if (chosen) {
                                                values - value
                                            } else if (field.type == "array") {
                                                values + value
                                            } else {
                                                listOf(value)
                                            }
                                        dispatch(AgentIntent.AnswerQuestion(question.id, field.id, selected))
                                    },
                                )
                            }
                        }
                    } else {
                        BasicTextField(
                            values.firstOrNull().orEmpty(),
                            {
                                dispatch(
                                    AgentIntent.AnswerQuestion(
                                        question.id,
                                        field.id,
                                        if (it.isEmpty()) emptyList() else listOf(it),
                                    ),
                                )
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = field.title }
                                    .background(colors.inputBackground)
                                    .padding(Spacing.sm.dp),
                        )
                    }
                }
                question.error?.let { Text(it, color = colors.textPrimary, style = MaterialTheme.typography.bodySmall) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
                IntelliJButton(
                    "Send answer",
                    onClick = { dispatch(AgentIntent.ResolveQuestion(question.id, "accept")) },
                )
                IntelliJButton("Decline", onClick = { dispatch(AgentIntent.ResolveQuestion(question.id, "decline")) })
                IntelliJButton(
                    "Cancel question",
                    onClick = { dispatch(AgentIntent.ResolveQuestion(question.id, "cancel")) },
                )
            }
        }
    }
}
