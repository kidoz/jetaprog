package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.viewmodel.AgentMessage
import su.kidoz.jetaprog.app.viewmodel.AgentMessageRole
import su.kidoz.jetaprog.app.viewmodel.AgentSessionViewModel
import su.kidoz.jetaprog.app.viewmodel.AgentToolCallView

/**
 * The agent tool window: connect to an Agent Client Protocol agent, send prompts
 * and observe streamed responses, tool calls and plans.
 *
 * @param viewModel the session view model backing the panel.
 * @param modifier the layout modifier.
 */
@Composable
public fun AgentPanel(
    viewModel: AgentSessionViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        ConnectionBar(
            command = state.agentCommand,
            connected = state.connected,
            connecting = state.connecting,
            agentName = state.agentName,
            onCommandChange = viewModel::setAgentCommand,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
        )

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.messages) { message -> MessageRow(message) }

            if (state.toolCalls.isNotEmpty()) {
                items(state.toolCalls) { toolCall -> ToolCallRow(toolCall) }
            }

            if (state.plan.isNotEmpty()) {
                items(state.plan) { entry ->
                    Text(
                        text = "• ${entry.content}" + (entry.status?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        PromptInput(
            value = input,
            enabled = state.connected,
            running = state.isRunning,
            onValueChange = { input = it },
            onSend = {
                viewModel.sendPrompt(input)
                input = ""
            },
            onCancel = viewModel::cancelTurn,
        )
    }

    state.pendingPermission?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.resolvePermission(null) },
            title = { Text("Permission requested") },
            text = { Text(prompt.title) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    prompt.options.forEach { option ->
                        TextButton(onClick = { viewModel.resolvePermission(option.optionId) }) {
                            Text(option.name)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolvePermission(null) }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ConnectionBar(
    command: String,
    connected: Boolean,
    connecting: Boolean,
    agentName: String?,
    onCommandChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = command,
            onValueChange = onCommandChange,
            enabled = !connected && !connecting,
            singleLine = true,
            label = { Text("Agent command") },
            modifier = Modifier.weight(1f),
        )
        if (connected) {
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        } else {
            Button(onClick = onConnect, enabled = !connecting) {
                Text(if (connecting) "Connecting…" else "Connect")
            }
        }
        agentName?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun MessageRow(message: AgentMessage) {
    val color =
        when (message.role) {
            AgentMessageRole.USER -> MaterialTheme.colorScheme.primary
            AgentMessageRole.AGENT -> MaterialTheme.colorScheme.onSurface
            AgentMessageRole.THOUGHT -> MaterialTheme.colorScheme.onSurfaceVariant
            AgentMessageRole.SYSTEM -> MaterialTheme.colorScheme.outline
        }
    val prefix =
        when (message.role) {
            AgentMessageRole.USER -> "You"
            AgentMessageRole.AGENT -> "Agent"
            AgentMessageRole.THOUGHT -> "Thinking"
            AgentMessageRole.SYSTEM -> "System"
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = prefix, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(
            text = message.text,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (message.role == AgentMessageRole.THOUGHT) FontStyle.Italic else FontStyle.Normal,
        )
    }
}

@Composable
private fun ToolCallRow(toolCall: AgentToolCallView) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = toolCall.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val detail = listOfNotNull(toolCall.kind, toolCall.status).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(text = detail, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun PromptInput(
    value: String,
    enabled: Boolean,
    running: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled && !running,
            label = { Text("Message the agent") },
            modifier = Modifier.weight(1f),
        )
        if (running) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.width(96.dp)) { Text("Cancel") }
        } else {
            Button(onClick = onSend, enabled = enabled && value.isNotBlank(), modifier = Modifier.width(96.dp)) {
                Text("Send")
            }
        }
    }
}
