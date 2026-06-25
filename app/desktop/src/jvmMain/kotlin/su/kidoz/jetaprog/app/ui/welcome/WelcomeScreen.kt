package su.kidoz.jetaprog.app.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.Spacing

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 3_600_000L
private const val DAY_MILLIS = 86_400_000L

/**
 * JetBrains-style Welcome Hub shown when no project is open.
 *
 * Left rail (navigation) + main pane (project actions, search, recent projects).
 * All actions are dispatched as [WelcomeIntent]s; the host wires the resulting
 * [WelcomeEffect]s to the real open/new/clone flows.
 *
 * @param viewModel The Welcome Hub view model.
 * @param nowEpochMillis Current wall-clock time, used to render relative timestamps.
 * @param modifier Modifier for the root container.
 */
@Composable
public fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    nowEpochMillis: Long,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Row(modifier = modifier.fillMaxSize().background(IntelliJColors.background)) {
        WelcomeRail()
        MainPane(
            state = state,
            nowEpochMillis = nowEpochMillis,
            onIntent = viewModel::dispatch,
            modifier = Modifier.weight(1f),
        )
    }
}

// ============================================================
// LEFT RAIL
// ============================================================

private data class RailEntry(
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun WelcomeRail() {
    val entries =
        remember {
            listOf(
                RailEntry("Projects", Icons.Filled.Folder),
                RailEntry("Remote Development", Icons.Filled.Cloud),
                RailEntry("Plugins", Icons.Filled.Extension),
                RailEntry("Customize", Icons.Filled.Tune),
                RailEntry("Learn", Icons.Filled.School),
            )
        }

    Column(
        modifier =
            Modifier
                .width(Dimensions.welcomeRailWidth.dp)
                .fillMaxHeight()
                .background(IntelliJColors.surface)
                .padding(14.dp),
    ) {
        BrandHeader()
        Spacer(Modifier.height(22.dp))
        entries.forEachIndexed { index, entry ->
            RailItem(entry = entry, selected = index == 0)
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.weight(1f))
        RailItem(entry = RailEntry("Settings", Icons.Filled.Settings), selected = false, muted = true)
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(IntelliJColors.brandGradientStart, IntelliJColors.brandGradientEnd),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "J",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetaProgFonts.jetBrainsMono,
            )
        }
        Spacer(Modifier.width(Spacing.sm.dp))
        Column {
            Text(
                text = "JetaProg",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = JetaProgFonts.jetBrainsMono,
            )
            Text(
                text = "2026.1 · Desktop",
                color = IntelliJColors.textMuted,
                fontSize = 10.sp,
                fontFamily = JetaProgFonts.jetBrainsMono,
            )
        }
    }
}

@Composable
private fun RailItem(
    entry: RailEntry,
    selected: Boolean,
    muted: Boolean = false,
) {
    val contentColor =
        when {
            selected -> Color.White
            muted -> IntelliJColors.textMuted
            else -> IntelliJColors.textSecondary
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.welcomeRailItemHeight.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (selected) IntelliJColors.accentSubtle else Color.Transparent)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = entry.label,
            color = contentColor,
            fontSize = if (muted) 12.sp else 13.sp,
            fontFamily = JetaProgFonts.jetBrainsMono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ============================================================
// MAIN PANE
// ============================================================

@Composable
private fun MainPane(
    state: WelcomeState,
    nowEpochMillis: Long,
    onIntent: (WelcomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(IntelliJColors.background)
                .padding(horizontal = 40.dp, vertical = 30.dp),
    ) {
        ActionBar(query = state.query, onIntent = onIntent)
        Spacer(Modifier.height(28.dp))
        Text(
            text = "RECENT PROJECTS",
            color = IntelliJColors.textMuted,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontFamily = JetaProgFonts.jetBrainsMono,
        )
        Spacer(Modifier.height(10.dp))
        if (state.filtered.isEmpty()) {
            EmptyRecents(hasQuery = state.query.isNotBlank())
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.filtered, key = { it.path }) { project ->
                    RecentRow(
                        project = project,
                        nowEpochMillis = nowEpochMillis,
                        onOpen = { onIntent(WelcomeIntent.Open(project.path)) },
                        onRemove = { onIntent(WelcomeIntent.Remove(project.path)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionBar(
    query: String,
    onIntent: (WelcomeIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
    ) {
        SearchField(
            query = query,
            onQueryChange = { onIntent(WelcomeIntent.Search(it)) },
            modifier = Modifier.widthIn(max = 360.dp).weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        IntelliJButton(
            text = "New Project",
            onClick = { onIntent(WelcomeIntent.NewProject) },
            style = ButtonStyle.PRIMARY,
            icon = Icons.Filled.Add,
            modifier = Modifier.height(Dimensions.inputHeight.dp),
        )
        IntelliJButton(
            text = "Open",
            onClick = { onIntent(WelcomeIntent.OpenProject) },
            style = ButtonStyle.SECONDARY,
            icon = Icons.Filled.FolderOpen,
            modifier = Modifier.height(Dimensions.inputHeight.dp),
        )
        IntelliJButton(
            text = "Clone Repository",
            onClick = { onIntent(WelcomeIntent.Clone) },
            style = ButtonStyle.SECONDARY,
            icon = Icons.Filled.AccountTree,
            modifier = Modifier.height(Dimensions.inputHeight.dp),
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(Dimensions.inputHeight.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                .background(IntelliJColors.inputBackground)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = IntelliJColors.textMuted,
            modifier = Modifier.size(17.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = "Search projects",
                    color = IntelliJColors.textMuted,
                    fontSize = 13.sp,
                    fontFamily = JetaProgFonts.jetBrainsMono,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle =
                    TextStyle(
                        color = IntelliJColors.textPrimary,
                        fontSize = 13.sp,
                        fontFamily = JetaProgFonts.jetBrainsMono,
                    ),
                cursorBrush = SolidColor(IntelliJColors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecentRow(
    project: RecentProject,
    nowEpochMillis: Long,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.welcomeRecentRowHeight.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                .background(if (isHovered) IntelliJColors.welcomeRecentRowHover else Color.Transparent)
                .hoverable(interactionSource)
                .clickable(onClick = onOpen)
                .padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .background(project.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = project.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetaProgFonts.jetBrainsMono,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.name,
                color = IntelliJColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = JetaProgFonts.jetBrainsMono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = project.path,
                color = IntelliJColors.textMuted,
                fontSize = 12.sp,
                fontFamily = JetaProgFonts.jetBrainsMono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = relativeTime(project.lastOpenedEpochMillis, nowEpochMillis),
            color = IntelliJColors.textMuted,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.jetBrainsMono,
        )
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "Remove from recent projects",
            tint = IntelliJColors.scrollbarThumb,
            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onRemove),
        )
    }
}

@Composable
private fun EmptyRecents(hasQuery: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Text(
            text = if (hasQuery) "No projects match your search." else "No recent projects yet.",
            color = IntelliJColors.textMuted,
            fontSize = 13.sp,
            fontFamily = JetaProgFonts.jetBrainsMono,
        )
    }
}

/** Formats an epoch-millis timestamp as a coarse relative time (e.g. "2 hours ago"). */
private fun relativeTime(
    thenEpochMillis: Long,
    nowEpochMillis: Long,
): String {
    val delta = (nowEpochMillis - thenEpochMillis).coerceAtLeast(0)
    return when {
        delta < MINUTE_MILLIS -> "just now"
        delta < HOUR_MILLIS -> pluralize(delta / MINUTE_MILLIS, "minute")
        delta < DAY_MILLIS -> pluralize(delta / HOUR_MILLIS, "hour")
        else -> pluralize(delta / DAY_MILLIS, "day")
    }
}

private fun pluralize(
    value: Long,
    unit: String,
): String {
    val suffix = if (value == 1L) "" else "s"
    return "$value $unit$suffix ago"
}
