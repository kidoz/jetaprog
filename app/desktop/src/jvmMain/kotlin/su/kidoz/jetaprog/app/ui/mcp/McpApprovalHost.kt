package su.kidoz.jetaprog.app.ui.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.mcp.McpApprovalRequests
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.dialogs.IntelliJDialog
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

/**
 * Renders the pending MCP approval, if any, as a modal Allow / Deny dialog.
 */
@Composable
public fun McpApprovalHost(requests: McpApprovalRequests) {
    val request by requests.requests.collectAsState(initial = null)
    val current = request ?: return
    IntelliJDialog(
        onDismissRequest = { current.deny() },
        minWidth = Dimensions.dialogMinWidth.dp,
        maxWidth = Dimensions.dialogMinWidth.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
        ) {
            Text(
                text = current.title,
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = current.message,
                color = LocalIntelliJColors.current.textSecondary,
                fontSize = 12.sp,
                fontFamily = JetaProgFonts.codeFont,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp, Alignment.End),
            ) {
                IntelliJButton(text = "Deny", onClick = { current.deny() })
                IntelliJButton(text = "Allow", onClick = { current.allow() }, style = ButtonStyle.PRIMARY)
            }
        }
    }
}
