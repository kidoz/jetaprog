package su.kidoz.jetaprog.app.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.plugin.BundledPluginCatalog
import su.kidoz.jetaprog.app.ui.components.ButtonStyle
import su.kidoz.jetaprog.app.ui.components.IntelliJButton
import su.kidoz.jetaprog.app.ui.components.IntelliJCheckbox
import su.kidoz.jetaprog.app.ui.components.PopupListRow
import su.kidoz.jetaprog.app.ui.components.popupChrome
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsIntent
import su.kidoz.jetaprog.app.ui.dialogs.settings.SettingsState
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.JetaProgFonts
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.viewmodel.SettingsViewModel
import su.kidoz.jetaprog.settings.model.Theme
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

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
    settingsViewModel: SettingsViewModel,
    nowEpochMillis: Long,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    var selectedRailIndex by remember { mutableStateOf(0) }

    Row(modifier = modifier.fillMaxSize().background(LocalIntelliJColors.current.background)) {
        WelcomeRail(
            selectedIndex = selectedRailIndex,
            onSelect = { selectedRailIndex = it },
            onOpenSettings = onOpenSettings,
        )
        when (selectedRailIndex) {
            0 -> {
                MainPane(
                    state = state,
                    nowEpochMillis = nowEpochMillis,
                    onIntent = viewModel::dispatch,
                    modifier = Modifier.weight(1f),
                )
            }

            2 -> {
                PluginsPane(
                    settingsState = settingsState,
                    onTogglePlugin = { id, enabled ->
                        settingsViewModel.dispatch(SettingsIntent.TogglePlugin(id, enabled))
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            3 -> {
                CustomizePane(
                    settingsState = settingsState,
                    onSetTheme = { theme ->
                        settingsViewModel.dispatch(SettingsIntent.SetTheme(theme))
                        settingsViewModel.dispatch(SettingsIntent.Apply)
                    },
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
            }

            4 -> {
                LearnPane(
                    onIntent = viewModel::dispatch,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
            }

            else -> {
                RailPlaceholderPane(
                    entry = RAIL_ENTRIES[selectedRailIndex],
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ============================================================
// LEFT RAIL
// ============================================================

private data class RailEntry(
    val label: String,
    val icon: ImageVector,
)

private val RAIL_ENTRIES =
    listOf(
        RailEntry("Projects", Icons.Filled.Folder),
        RailEntry("Remote Development", Icons.Filled.Cloud),
        RailEntry("Plugins", Icons.Filled.Extension),
        RailEntry("Customize", Icons.Filled.Tune),
        RailEntry("Learn", Icons.Filled.School),
    )

@Composable
private fun WelcomeRail(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(Dimensions.welcomeRailWidth.dp)
                .fillMaxHeight()
                .background(LocalIntelliJColors.current.surface)
                .padding(14.dp),
    ) {
        BrandHeader()
        Spacer(Modifier.height(22.dp))
        RAIL_ENTRIES.forEachIndexed { index, entry ->
            RailItem(
                entry = entry,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.weight(1f))
        RailItem(
            entry = RailEntry("Settings", Icons.Filled.Settings),
            selected = false,
            muted = true,
            onClick = onOpenSettings,
        )
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
                            listOf(
                                LocalIntelliJColors.current.brandGradientStart,
                                LocalIntelliJColors.current.brandGradientEnd,
                            ),
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
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = JetaProgFonts.jetBrainsMono,
            )
            Text(
                text = "2026.1 · Desktop",
                color = LocalIntelliJColors.current.textMuted,
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
    onClick: (() -> Unit)? = null,
) {
    val contentColor =
        when {
            selected -> LocalIntelliJColors.current.textPrimary
            muted -> LocalIntelliJColors.current.textMuted
            else -> LocalIntelliJColors.current.textSecondary
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.welcomeRailItemHeight.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (selected) LocalIntelliJColors.current.accentSubtle else Color.Transparent)
                .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
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

/** Placeholder main pane for rail tabs that are not implemented yet. */
@Composable
private fun RailPlaceholderPane(
    entry: RailEntry,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .background(LocalIntelliJColors.current.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = LocalIntelliJColors.current.textMuted,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(Spacing.md.dp))
            Text(
                text = entry.label,
                color = LocalIntelliJColors.current.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = JetaProgFonts.jetBrainsMono,
            )
            Spacer(Modifier.height(Spacing.xs.dp))
            Text(
                text = "Coming soon.",
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 12.sp,
                fontFamily = JetaProgFonts.jetBrainsMono,
            )
        }
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
                .background(LocalIntelliJColors.current.background)
                .padding(horizontal = 40.dp, vertical = 30.dp),
    ) {
        ActionBar(query = state.query, onIntent = onIntent)
        Spacer(Modifier.height(28.dp))
        Text(
            text = "RECENT PROJECTS",
            color = LocalIntelliJColors.current.textMuted,
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
                        onOpenInNewWindow = { onIntent(WelcomeIntent.OpenInNewWindow(project.path)) },
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
                .background(LocalIntelliJColors.current.inputBackground)
                .border(
                    width = 1.dp,
                    color = LocalIntelliJColors.current.border,
                    shape = RoundedCornerShape(Dimensions.cornerRadiusLarge.dp),
                ).padding(horizontal = Spacing.md.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = LocalIntelliJColors.current.textMuted,
            modifier = Modifier.size(17.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = "Search projects",
                    color = LocalIntelliJColors.current.textMuted,
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
                        color = LocalIntelliJColors.current.textPrimary,
                        fontSize = 13.sp,
                        fontFamily = JetaProgFonts.jetBrainsMono,
                    ),
                cursorBrush = SolidColor(LocalIntelliJColors.current.accent),
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
    onOpenInNewWindow: () -> Unit,
    onRemove: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimensions.welcomeRecentRowHeight.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                .background(if (isHovered) LocalIntelliJColors.current.welcomeRecentRowHover else Color.Transparent)
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
                    .background(welcomeTileAccent(index = project.accentIndex)),
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
                color = LocalIntelliJColors.current.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = JetaProgFonts.jetBrainsMono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = project.path,
                color = LocalIntelliJColors.current.textMuted,
                fontSize = 12.sp,
                fontFamily = JetaProgFonts.jetBrainsMono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = relativeTime(project.lastOpenedEpochMillis, nowEpochMillis),
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.jetBrainsMono,
        )
        var menuAnchorPx by remember { mutableStateOf(0) }
        Box(modifier = Modifier.onSizeChanged { menuAnchorPx = it.height }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Project options",
                tint = LocalIntelliJColors.current.scrollbarThumb,
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = { menuExpanded = true }),
            )
            RecentRowMenu(
                expanded = menuExpanded,
                offsetY = menuAnchorPx,
                onDismiss = { menuExpanded = false },
                onOpenInNewWindow = onOpenInNewWindow,
                onCopyPath = { copyToClipboard(project.path) },
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun RecentRowMenu(
    expanded: Boolean,
    offsetY: Int,
    onDismiss: () -> Unit,
    onOpenInNewWindow: () -> Unit,
    onCopyPath: () -> Unit,
    onRemove: () -> Unit,
) {
    if (expanded) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(0, offsetY),
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            Column(
                modifier =
                    Modifier
                        .width(Dimensions.menuWidth.dp)
                        .popupChrome(Dimensions.cornerRadius.dp)
                        .padding(vertical = Spacing.xs.dp),
            ) {
                RecentRowMenuItem(text = "Remove from list") {
                    onDismiss()
                    onRemove()
                }
                RecentRowMenuItem(text = "Open in new window") {
                    onDismiss()
                    onOpenInNewWindow()
                }
                RecentRowMenuItem(text = "Copy path") {
                    onDismiss()
                    onCopyPath()
                }
            }
        }
    }
}

@Composable
private fun RecentRowMenuItem(
    text: String,
    onClick: () -> Unit,
) {
    PopupListRow(selected = false, onClick = onClick, height = Dimensions.popupRowHeightCompact.dp) {
        Text(
            text = text,
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.jetBrainsMono,
        )
    }
}

/** Puts [text] on the system clipboard. */
private fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

@Composable
private fun EmptyRecents(hasQuery: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
        Text(
            text = if (hasQuery) "No projects match your search." else "No recent projects yet.",
            color = LocalIntelliJColors.current.textMuted,
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

/**
 * Stable accent palette for the welcome project tiles (matches the design
 * references), resolved from the active palette at render time.
 */
@Composable
private fun welcomeTileAccent(index: Int): Color {
    val palette = LocalIntelliJColors.current
    val colors =
        listOf(
            palette.iconKotlin,
            palette.accent,
            palette.success,
            palette.warning,
            palette.iconJava,
            palette.debugVarType,
            palette.terminalMagenta,
        )
    return colors[index % colors.size]
}

// ============================================================
// PLUGINS PANE
// ============================================================

/**
 * Bundled plugins with their enable toggles, driven by the `disabledPlugins`
 * setting (the same ids gate plugin activation in `ProjectSession`).
 */
@Composable
private fun PluginsPane(
    settingsState: SettingsState,
    onTogglePlugin: (id: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val disabled = settingsState.plugins.disabledPlugins
    val enabledCount = BundledPluginCatalog.plugins.count { it.id !in disabled }

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(LocalIntelliJColors.current.background)
                .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Text(
            text = "Plugins",
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = JetaProgFonts.jetBrainsMono,
        )
        Spacer(Modifier.height(Spacing.xs.dp))
        Text(
            text = "$enabledCount of ${BundledPluginCatalog.plugins.size} bundled plugins enabled",
            color = LocalIntelliJColors.current.textMuted,
            fontSize = 12.sp,
            fontFamily = JetaProgFonts.jetBrainsMono,
        )
        Spacer(Modifier.height(Spacing.lg.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(BundledPluginCatalog.plugins, key = { it.id }) { plugin ->
                val enabled = plugin.id !in disabled
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                            .background(LocalIntelliJColors.current.surface)
                            .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = plugin.name,
                            color =
                                if (enabled) {
                                    LocalIntelliJColors.current.textPrimary
                                } else {
                                    LocalIntelliJColors.current.textDisabled
                                },
                            fontSize = 13.sp,
                            fontFamily = JetaProgFonts.jetBrainsMono,
                        )
                        Text(
                            text = plugin.description,
                            color = LocalIntelliJColors.current.textMuted,
                            fontSize = 11.sp,
                            fontFamily = JetaProgFonts.jetBrainsMono,
                        )
                    }
                    Text(
                        text = plugin.version,
                        color = LocalIntelliJColors.current.textMuted,
                        fontSize = 11.sp,
                        fontFamily = JetaProgFonts.jetBrainsMono,
                    )
                    Spacer(Modifier.width(Spacing.md.dp))
                    IntelliJCheckbox(
                        checked = enabled,
                        onCheckedChange = { checked -> onTogglePlugin(plugin.id, checked) },
                        label = if (enabled) "Enabled" else "Disabled",
                    )
                }
            }
        }
    }
}

// ============================================================
// CUSTOMIZE PANE
// ============================================================

/**
 * Quick appearance controls: the color theme picker (applied immediately) and
 * a shortcut into the full Settings dialog.
 */
@Composable
private fun CustomizePane(
    settingsState: SettingsState,
    onSetTheme: (Theme) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(LocalIntelliJColors.current.background)
                .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Text(
            text = "Customize",
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = JetaProgFonts.jetBrainsMono,
        )
        Spacer(Modifier.height(Spacing.lg.dp))
        Text(
            text = "Color theme",
            color = LocalIntelliJColors.current.textSecondary,
            fontSize = 13.sp,
            fontFamily = JetaProgFonts.jetBrainsMono,
        )
        Spacer(Modifier.height(Spacing.sm.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp)) {
            Theme.entries.forEach { theme ->
                IntelliJButton(
                    text = theme.displayName,
                    onClick = { onSetTheme(theme) },
                    style =
                        if (settingsState.appearance.theme == theme) {
                            ButtonStyle.PRIMARY
                        } else {
                            ButtonStyle.SECONDARY
                        },
                )
            }
        }
        Spacer(Modifier.height(Spacing.xl.dp))
        IntelliJButton(
            text = "More Appearance Settings...",
            onClick = onOpenSettings,
            style = ButtonStyle.SECONDARY,
        )
    }
}

// ============================================================
// LEARN PANE
// ============================================================

/** One shortcut row in the Learn pane's keymap cheat-sheet. */
private data class ShortcutTip(
    val action: String,
    val keys: String,
)

private val SHORTCUT_TIPS =
    listOf(
        ShortcutTip("Search everywhere", "Double Shift"),
        ShortcutTip("Go to class", "Ctrl+N"),
        ShortcutTip("Go to file", "Ctrl+Shift+N"),
        ShortcutTip("Recent files", "Ctrl+E"),
        ShortcutTip("Go to declaration", "Ctrl+B"),
        ShortcutTip("Rename", "Shift+F6"),
        ShortcutTip("Command palette", "Ctrl+Shift+A"),
    )

/**
 * Getting-started cards and a keymap cheat-sheet built from the real default
 * keymap, so the hints never drift from the actual bindings.
 */
@Composable
private fun LearnPane(
    onIntent: (WelcomeIntent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(LocalIntelliJColors.current.background)
                .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Text(
            text = "Learn",
            color = LocalIntelliJColors.current.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = JetaProgFonts.jetBrainsMono,
        )
        Spacer(Modifier.height(Spacing.lg.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md.dp)) {
            IntelliJButton(
                text = "New Project",
                onClick = { onIntent(WelcomeIntent.NewProject) },
                style = ButtonStyle.SECONDARY,
            )
            IntelliJButton(
                text = "Open Project",
                onClick = { onIntent(WelcomeIntent.OpenProject) },
                style = ButtonStyle.SECONDARY,
            )
            IntelliJButton(
                text = "Editor Settings",
                onClick = onOpenSettings,
                style = ButtonStyle.SECONDARY,
            )
        }
        Spacer(Modifier.height(Spacing.xl.dp))
        Text(
            text = "Essential shortcuts",
            color = LocalIntelliJColors.current.textSecondary,
            fontSize = 13.sp,
            fontFamily = JetaProgFonts.jetBrainsMono,
        )
        Spacer(Modifier.height(Spacing.sm.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(SHORTCUT_TIPS, key = { it.action }) { tip ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                            .background(LocalIntelliJColors.current.surface)
                            .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = tip.action,
                        color = LocalIntelliJColors.current.textPrimary,
                        fontSize = 12.sp,
                        fontFamily = JetaProgFonts.jetBrainsMono,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = tip.keys,
                        color = LocalIntelliJColors.current.accent,
                        fontSize = 12.sp,
                        fontFamily = JetaProgFonts.jetBrainsMono,
                    )
                }
            }
        }
    }
}
