package su.kidoz.jetaprog.app.gallery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.agent.AgentIntent
import su.kidoz.jetaprog.app.ui.agent.AgentQuestion
import su.kidoz.jetaprog.app.ui.agent.AgentQuestionField
import su.kidoz.jetaprog.app.ui.agent.AgentQuestionSection
import su.kidoz.jetaprog.app.ui.agent.AgentReviewFeedback
import su.kidoz.jetaprog.app.ui.agent.AgentUiState
import su.kidoz.jetaprog.app.ui.agent.ReviewComment
import su.kidoz.jetaprog.app.ui.agent.ReviewScope
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme

/** Structured questions and batched review feedback using production components. */
@Composable
internal fun AgentCommunicationSpecimen() {
    var question by remember {
        mutableStateOf(
            AgentQuestion(
                "question",
                "How should I approach this refactoring?",
                listOf(
                    AgentQuestionField(
                        "strategy",
                        "Strategy",
                        null,
                        "string",
                        true,
                        mapOf("safe" to "Conservative", "balanced" to "Balanced"),
                    ),
                ),
            ),
        )
    }
    Column(Modifier.width(Dimensions.popupSearchWidth.dp)) {
        AgentQuestionSection(question, "Agent", { intent ->
            if (intent is AgentIntent.AnswerQuestion) {
                question = question.copy(answers = question.answers + (intent.field to intent.values))
            }
        }, Dimensions.popupCompletionMaxHeight.dp)
        AgentReviewFeedback(
            AgentUiState(
                reviewComments =
                    listOf(
                        ReviewComment(
                            "review",
                            "src/Reader.kt",
                            "12-18",
                            "Handle end of file here.",
                            ReviewScope.AGENT,
                        ),
                    ),
            ),
            listOf("src/Reader.kt"),
            {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun AgentCommunicationDarkPreview() {
    JetaProgTheme { AgentCommunicationSpecimen() }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun AgentCommunicationLightPreview() {
    JetaProgTheme(darkTheme = false) { AgentCommunicationSpecimen() }
}
