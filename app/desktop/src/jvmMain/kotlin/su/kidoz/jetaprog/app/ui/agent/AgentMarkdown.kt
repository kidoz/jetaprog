package su.kidoz.jetaprog.app.ui.agent

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors

/**
 * Renders an agent message as Markdown using the IDE design tokens.
 *
 * ACP agents send `text` content blocks whose payload is Markdown by convention. The previous
 * render is retained while an updated chunk is re-parsed, so streaming output does not flicker.
 */
@Composable
internal fun AgentMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIntelliJColors.current
    val state = rememberMarkdownState(content = markdown, retainState = true)

    val body = TextStyle(color = colors.textPrimary, fontSize = 13.sp)
    val code = TextStyle(color = colors.textPrimary, fontFamily = JetaProgFonts.codeFont, fontSize = 12.sp)

    SelectionContainer {
        Markdown(
            markdownState = state,
            modifier = modifier,
            colors =
                markdownColor(
                    text = colors.textPrimary,
                    codeBackground = colors.codeBlockBackground,
                    inlineCodeBackground = colors.codeBlockBackground,
                    dividerColor = colors.divider,
                    tableBackground = colors.surface,
                ),
            typography =
                markdownTypography(
                    h1 = body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    h2 = body.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    h3 = body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    h4 = body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    h5 = body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    h6 = body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    text = body,
                    paragraph = body,
                    ordered = body,
                    bullet = body,
                    list = body,
                    table = body,
                    code = code,
                    inlineCode = code.copy(fontSize = 13.sp),
                    quote = body.copy(color = colors.textSecondary, fontStyle = FontStyle.Italic),
                    textLink =
                        TextLinkStyles(
                            style = SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline),
                        ),
                ),
        )
    }
}
