package su.kidoz.jetaprog.app.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.vcs.GitChange
import su.kidoz.jetaprog.vcs.GitChangeType
import kotlin.math.abs

/** Deterministic avatar/graph palette for commit authors. */
internal val GIT_AVATAR_PALETTE: List<Color> =
    listOf(
        Color(0xFF5B9BD5),
        Color(0xFF7F52FF),
        Color(0xFF59A869),
        Color(0xFFE0883C),
        Color(0xFF9876AA),
    )

/** Picks a stable color for [seed] (e.g. an author name or branch ref). */
internal fun gitColorFor(seed: String): Color = GIT_AVATAR_PALETTE[abs(seed.hashCode()) % GIT_AVATAR_PALETTE.size]

/** Up to two uppercase initials for an author display name. */
internal fun authorInitials(author: String): String =
    author
        .split(' ', '.', '-')
        .mapNotNull { it.firstOrNull() }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifBlank { "?" }

/** A circular author avatar with initials. */
@Composable
internal fun Avatar(
    author: String,
    size: Dp = 18.dp,
) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(gitColorFor(author)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = authorInitials(author), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

/** A small colored file-type badge with a single letter. */
@Composable
internal fun FileBadge(fileName: String) {
    val (color, label) = badgeFor(fileName)
    Box(
        modifier = Modifier.size(15.dp).clip(RoundedCornerShape(3.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

internal fun badgeFor(fileName: String): Pair<Color, String> =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> IntelliJColors.iconKotlin to "K"
        "java" -> IntelliJColors.iconJava to "J"
        "rs" -> IntelliJColors.iconRust to "R"
        "py" -> IntelliJColors.iconPython to "P"
        "" -> IntelliJColors.iconFile to "•"
        else -> IntelliJColors.iconFile to fileName.first().uppercase()
    }

internal fun GitChangeType.statusLabel(): String =
    when (this) {
        GitChangeType.ADDED -> "A"
        GitChangeType.MODIFIED -> "M"
        GitChangeType.DELETED -> "D"
        GitChangeType.RENAMED -> "R"
        GitChangeType.COPIED -> "C"
        GitChangeType.UNTRACKED -> "?"
        GitChangeType.CONFLICTED -> "!"
        GitChangeType.UNKNOWN -> "?"
    }

@Composable
internal fun GitChangeType.statusColor(): Color =
    when (this) {
        GitChangeType.ADDED -> IntelliJColors.success
        GitChangeType.MODIFIED -> IntelliJColors.info
        GitChangeType.DELETED -> IntelliJColors.error
        GitChangeType.RENAMED -> IntelliJColors.warning
        GitChangeType.COPIED -> IntelliJColors.warning
        GitChangeType.UNTRACKED -> IntelliJColors.textSecondary
        GitChangeType.CONFLICTED -> IntelliJColors.error
        GitChangeType.UNKNOWN -> IntelliJColors.textMuted
    }

internal fun GitChange.fileName(): String = path.substringAfterLast('/')

internal fun GitChange.parentPath(): String = path.substringBeforeLast('/', missingDelimiterValue = "")
