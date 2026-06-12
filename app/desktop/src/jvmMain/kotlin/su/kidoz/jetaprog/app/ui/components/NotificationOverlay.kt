package su.kidoz.jetaprog.app.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import su.kidoz.jetaprog.app.notification.Notification
import su.kidoz.jetaprog.app.notification.NotificationCenter
import su.kidoz.jetaprog.app.notification.NotificationSeverity
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.Elevation
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing

private const val MAX_VISIBLE_TOASTS = 3

/**
 * Renders the active toast stack at the bottom-right of the host [Box].
 *
 * Use as the last child of the main shell so it overlays every other surface
 * but stays beneath modal dialogs. The overlay subscribes to [center] and
 * auto-dismisses each notification after its [Notification.autoDismissMs].
 */
@Composable
public fun NotificationOverlay(center: NotificationCenter) {
    val notifications by center.notifications.collectAsState()
    val visible = notifications.takeLast(MAX_VISIBLE_TOASTS)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier = Modifier.padding(Dimensions.notificationStackPadding.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
            horizontalAlignment = Alignment.End,
        ) {
            visible.forEach { notification ->
                key(notification.id) {
                    NotificationToast(
                        notification = notification,
                        onDismiss = { center.dismiss(notification.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationToast(
    notification: Notification,
    onDismiss: () -> Unit,
) {
    notification.autoDismissMs?.let { ms ->
        LaunchedEffect(notification.id) {
            delay(ms)
            onDismiss()
        }
    }

    val stripe = notification.severity.stripe()
    val icon = notification.severity.icon()
    val iconTint = notification.severity.iconTint()

    Row(
        modifier =
            Modifier
                .widthIn(max = Dimensions.notificationWidth.dp)
                .width(Dimensions.notificationWidth.dp)
                .wrapContentHeight()
                .shadow(Elevation.popup.dp, RoundedCornerShape(Dimensions.cornerRadius.dp))
                .clip(RoundedCornerShape(Dimensions.cornerRadius.dp))
                .background(IntelliJColors.notificationBackground)
                .border(
                    width = 1.dp,
                    color = IntelliJColors.notificationBorder,
                    shape = RoundedCornerShape(Dimensions.cornerRadius.dp),
                ),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .width(Dimensions.notificationStripeWidth.dp)
                    .fillMaxHeight()
                    .background(stripe),
        )

        Row(
            modifier = Modifier.padding(Spacing.md.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = notification.severity.contentDescription(),
                tint = iconTint,
                modifier = Modifier.size(Dimensions.iconMd.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs.dp),
            ) {
                Text(
                    text = notification.title,
                    color = IntelliJColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                notification.message?.let {
                    Text(
                        text = it,
                        color = IntelliJColors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                if (notification.actionLabel != null && notification.onAction != null) {
                    Spacer(modifier = Modifier.size(Spacing.xxs.dp))
                    NotificationActionLink(
                        label = notification.actionLabel,
                        onClick = {
                            notification.onAction.invoke()
                            onDismiss()
                        },
                    )
                }
            }

            CloseButton(onClick = onDismiss)
        }
    }
}

@Composable
private fun NotificationActionLink(
    label: String,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Text(
        text = label,
        color = if (hovered) IntelliJColors.accentHover else IntelliJColors.textLink,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            Modifier
                .hoverable(source)
                .clickable(onClick = onClick),
    )
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier =
            Modifier
                .size(Dimensions.iconMd.dp)
                .clip(RoundedCornerShape(Dimensions.cornerRadiusSmall.dp))
                .background(if (hovered) IntelliJColors.surfaceHover else Color.Transparent)
                .hoverable(source)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Dismiss notification",
            tint = IntelliJColors.textSecondary,
            modifier = Modifier.size(Dimensions.iconXs.dp),
        )
    }
}

private fun NotificationSeverity.stripe(): Color =
    when (this) {
        NotificationSeverity.INFO -> IntelliJColors.notificationInfoStripe
        NotificationSeverity.SUCCESS -> IntelliJColors.notificationSuccessStripe
        NotificationSeverity.WARNING -> IntelliJColors.notificationWarningStripe
        NotificationSeverity.ERROR -> IntelliJColors.notificationErrorStripe
    }

private fun NotificationSeverity.icon(): ImageVector =
    when (this) {
        NotificationSeverity.INFO -> Icons.Default.Info
        NotificationSeverity.SUCCESS -> Icons.Default.CheckCircle
        NotificationSeverity.WARNING -> Icons.Default.Warning
        NotificationSeverity.ERROR -> Icons.Default.Error
    }

private fun NotificationSeverity.iconTint(): Color = stripe()

private fun NotificationSeverity.contentDescription(): String =
    when (this) {
        NotificationSeverity.INFO -> "Information"
        NotificationSeverity.SUCCESS -> "Success"
        NotificationSeverity.WARNING -> "Warning"
        NotificationSeverity.ERROR -> "Error"
    }
