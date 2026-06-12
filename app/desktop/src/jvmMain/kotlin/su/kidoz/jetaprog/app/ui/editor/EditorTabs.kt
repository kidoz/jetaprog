package su.kidoz.jetaprog.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.editor.state.EditorTab

/**
 * Tab bar for open editor tabs.
 */
@Composable
public fun EditorTabs(
    tabs: List<EditorTab>,
    activeTabIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            EditorTabItem(
                tab = tab,
                isActive = index == activeTabIndex,
                onClick = { onTabClick(index) },
                onClose = { onTabClose(index) },
            )
        }
    }
}

@Composable
private fun EditorTabItem(
    tab: EditorTab,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val backgroundColor =
        if (isActive) {
            MaterialTheme.colorScheme.background
        } else {
            MaterialTheme.colorScheme.surface
        }

    val textColor =
        if (isActive) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        }

    Row(
        modifier =
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Modified indicator
        if (tab.isDirty) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(end = 4.dp),
            )
        }

        // Tab name
        Text(
            text = tab.name,
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (tab.isDirty) 4.dp else 0.dp),
        )

        // Close button
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close tab",
            tint = textColor.copy(alpha = 0.6f),
            modifier =
                Modifier
                    .padding(start = 8.dp)
                    .size(14.dp)
                    .clickable(onClick = onClose),
        )
    }
}
