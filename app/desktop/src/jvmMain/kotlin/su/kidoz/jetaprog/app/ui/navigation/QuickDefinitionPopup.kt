package su.kidoz.jetaprog.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import su.kidoz.jetaprog.editor.navigation.QuickInfo

/**
 * Quick definition popup for peeking at symbol definitions (Ctrl+Shift+I).
 */
@Composable
public fun QuickDefinitionPopup(
    isVisible: Boolean,
    quickInfo: QuickInfo?,
    onNavigateToDefinition: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isVisible || quickInfo == null) return

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            focusRequester.requestFocus()
        }
    }

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier =
                modifier
                    .width(600.dp)
                    .shadow(8.dp, RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .clip(RoundedCornerShape(Dimensions.cornerRadiusLarge.dp))
                    .background(IntelliJColors.popupBackground)
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        when (keyEvent.key) {
                            Key.Escape -> {
                                onDismiss()
                                true
                            }

                            Key.Enter -> {
                                onNavigateToDefinition()
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
        ) {
            // Header with symbol info
            QuickDefinitionHeader(
                quickInfo = quickInfo,
                onNavigateToDefinition = onNavigateToDefinition,
            )

            HorizontalDivider(
                color = IntelliJColors.border,
                thickness = 1.dp,
            )

            // Signature
            quickInfo.signature?.let { signature ->
                SignatureSection(signature = signature)
                HorizontalDivider(
                    color = IntelliJColors.border,
                    thickness = 1.dp,
                )
            }

            // Definition preview
            quickInfo.definitionPreview?.let { preview ->
                DefinitionPreviewSection(preview = preview)
            }

            // Documentation
            quickInfo.documentation?.let { documentation ->
                if (quickInfo.definitionPreview != null) {
                    HorizontalDivider(
                        color = IntelliJColors.border,
                        thickness = 1.dp,
                    )
                }
                DocumentationSection(documentation = documentation)
            }

            // Footer
            QuickDefinitionFooter()
        }
    }
}

@Composable
private fun QuickDefinitionHeader(
    quickInfo: QuickInfo,
    onNavigateToDefinition: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
    ) {
        // Symbol icon
        Icon(
            imageVector = quickInfo.symbol.kind.toQuickDefIcon(),
            contentDescription = null,
            tint = quickInfo.symbol.kind.toQuickDefColor(),
            modifier = Modifier.size(18.dp),
        )

        // Symbol name and container
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = quickInfo.symbol.name,
                color = IntelliJColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            quickInfo.symbol.containerName?.let { container ->
                Text(
                    text = container,
                    color = IntelliJColors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // File location
        Text(
            text = quickInfo.symbol.filePath.substringAfterLast('/'),
            color = IntelliJColors.textSecondary,
            fontSize = 11.sp,
        )

        // Navigate button
        IconButton(
            onClick = onNavigateToDefinition,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Go to definition",
                tint = IntelliJColors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SignatureSection(signature: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceContainer)
                .padding(Spacing.md.dp),
    ) {
        Text(
            text = signature,
            color = IntelliJColors.textPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DefinitionPreviewSection(preview: String) {
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 300.dp)
                .background(IntelliJColors.editorBackground)
                .verticalScroll(verticalScrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(Spacing.md.dp),
    ) {
        // Simple code preview with line numbers
        val lines = preview.lines()
        Row {
            // Line numbers
            Column(
                modifier = Modifier.padding(end = Spacing.md.dp),
            ) {
                lines.forEachIndexed { index, _ ->
                    Text(
                        text = "${index + 1}",
                        color = IntelliJColors.lineNumberText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // Code content
            Column {
                lines.forEach { line ->
                    Text(
                        text = line.ifEmpty { " " },
                        color = IntelliJColors.textPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentationSection(documentation: String) {
    val scrollState = rememberScrollState()

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 150.dp)
                .verticalScroll(scrollState)
                .padding(Spacing.md.dp),
    ) {
        Text(
            text = documentation,
            color = IntelliJColors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun QuickDefinitionFooter() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(IntelliJColors.surfaceElevated)
                .padding(horizontal = Spacing.md.dp, vertical = Spacing.sm.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg.dp),
    ) {
        QuickDefFooterHint("Enter", "Go to definition")
        QuickDefFooterHint("Esc", "Close")
    }
}

@Composable
private fun QuickDefFooterHint(
    shortcut: String,
    description: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = shortcut,
            color = IntelliJColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier
                    .background(
                        IntelliJColors.surfaceContainer,
                        RoundedCornerShape(2.dp),
                    ).padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Text(
            text = description,
            color = IntelliJColors.textMuted,
            fontSize = 11.sp,
        )
    }
}

private fun NavigationSymbolKind.toQuickDefIcon(): ImageVector =
    when (this) {
        NavigationSymbolKind.CLASS,
        NavigationSymbolKind.INTERFACE,
        NavigationSymbolKind.TRAIT,
        NavigationSymbolKind.ENUM,
        NavigationSymbolKind.STRUCT,
        NavigationSymbolKind.OBJECT,
        -> Icons.Default.Class

        NavigationSymbolKind.FUNCTION,
        NavigationSymbolKind.METHOD,
        NavigationSymbolKind.CONSTRUCTOR,
        -> Icons.Default.Functions

        NavigationSymbolKind.FILE -> Icons.Default.Description

        else -> Icons.Default.Code
    }

private fun NavigationSymbolKind.toQuickDefColor(): Color =
    when (this) {
        NavigationSymbolKind.CLASS,
        NavigationSymbolKind.INTERFACE,
        NavigationSymbolKind.TRAIT,
        -> IntelliJColors.iconKotlin

        NavigationSymbolKind.FUNCTION,
        NavigationSymbolKind.METHOD,
        -> IntelliJColors.iconJava

        NavigationSymbolKind.ENUM -> IntelliJColors.iconRust

        else -> IntelliJColors.textSecondary
    }
