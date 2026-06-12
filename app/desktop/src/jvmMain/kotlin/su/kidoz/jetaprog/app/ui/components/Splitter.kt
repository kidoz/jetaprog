package su.kidoz.jetaprog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.theme.Dimensions
import su.kidoz.jetaprog.app.ui.theme.IntelliJColors
import java.awt.Cursor

/**
 * Draggable handle that splits two horizontally-stacked siblings (left/right).
 *
 * The handle has a wide hit area for usability ([Dimensions.splitterHandleHitArea])
 * but draws only a 1 dp visible divider centered inside it. Drag deltas are
 * emitted in dp; the parent owns the resulting width and is responsible for
 * clamping to a sensible range.
 *
 * Cursor changes to the platform horizontal-resize cursor while hovered.
 */
@Composable
public fun VerticalDragHandle(
    onDelta: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val state =
        rememberDraggableState { deltaPx ->
            val deltaDp = with(density) { deltaPx.toDp() }
            onDelta(deltaDp)
        }
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .width(Dimensions.splitterHandleHitArea.dp)
                .pointerHoverIcon(HorizontalResizeCursor)
                .draggable(state, Orientation.Horizontal),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(Dimensions.splitterThickness.dp)
                    .background(IntelliJColors.divider),
        )
    }
}

/**
 * Draggable handle that splits two vertically-stacked siblings (top/bottom).
 *
 * Mirror of [VerticalDragHandle] for vertical resize. Drag deltas are in dp.
 */
@Composable
public fun HorizontalDragHandle(
    onDelta: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val state =
        rememberDraggableState { deltaPx ->
            val deltaDp = with(density) { deltaPx.toDp() }
            onDelta(deltaDp)
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(Dimensions.splitterHandleHitArea.dp)
                .pointerHoverIcon(VerticalResizeCursor)
                .draggable(state, Orientation.Vertical),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Dimensions.splitterThickness.dp)
                    .background(IntelliJColors.divider),
        )
    }
}

/**
 * Clamp a dp value to the inclusive range [[min]; [max]].
 */
public fun Dp.coerceInDp(
    min: Dp,
    max: Dp,
): Dp =
    if (this < min) {
        min
    } else if (this > max) {
        max
    } else {
        this
    }

private val HorizontalResizeCursor: PointerIcon = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))
private val VerticalResizeCursor: PointerIcon = PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))
