package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/** Items whose bounds touch [rect]. */
internal fun itemsTouching(bounds: Map<String, Rect>, rect: Rect): Set<String> =
    bounds.filterValues { it.overlaps(rect) }.keys

/** Tracks item positions and the rectangle being dragged; positions are in root coordinates. */
class MarqueeState internal constructor() {
    internal val itemBounds = mutableMapOf<String, Rect>()
    internal var areaCoordinates: LayoutCoordinates? = null

    /** The dragged rectangle in root coordinates, or null when not dragging. */
    internal var rect by mutableStateOf<Rect?>(null)

    /** Items under the rectangle right now (for live highlighting). */
    var touching by mutableStateOf<Set<String>>(emptySet())
        internal set

    /** Registers an item so the rectangle can find it. */
    @Composable
    fun Modifier.marqueeItem(id: String): Modifier {
        DisposableEffect(id) { onDispose { itemBounds.remove(id) } } // scrolled out of view
        return onGloballyPositioned { itemBounds[id] = it.boundsInRoot() }
    }

    internal fun update(start: Offset, current: Offset) {
        val origin = areaCoordinates?.let { it.localToRoot(Offset.Zero) } ?: Offset.Zero
        val r = Rect(
            left = minOf(start.x, current.x) + origin.x,
            top = minOf(start.y, current.y) + origin.y,
            right = maxOf(start.x, current.x) + origin.x,
            bottom = maxOf(start.y, current.y) + origin.y,
        )
        rect = r
        touching = itemsTouching(itemBounds, r)
    }

    internal fun finish(): Set<String> = touching.also {
        rect = null
        touching = emptySet()
    }
}

/**
 * Box ("window") selection: dragging inside this area draws a rectangle and, on release, passes
 * every item it touched to [onSelect]. Plain clicks still reach the items, since a drag only
 * starts once the pointer moves past the touch slop. Items opt in with `Modifier.marqueeItem(id)`.
 */
@Composable
fun MarqueeSelectionArea(
    onSelect: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable MarqueeState.() -> Unit,
) {
    val state = remember { MarqueeState() }
    val currentOnSelect by rememberUpdatedState(onSelect)
    Box(
        modifier = modifier
            .onGloballyPositioned { state.areaCoordinates = it }
            .pointerInput(Unit) {
                var start = Offset.Zero
                var current = Offset.Zero
                detectDragGestures(
                    onDragStart = { start = it; current = it; state.update(start, current) },
                    onDrag = { change, amount ->
                        change.consume()
                        current += amount
                        state.update(start, current)
                    },
                    onDragEnd = { state.finish().takeIf { it.isNotEmpty() }?.let(currentOnSelect) },
                    onDragCancel = { state.finish() },
                )
            },
    ) {
        state.content()
        val rect = state.rect
        if (rect != null) {
            val origin = state.areaCoordinates?.localToRoot(Offset.Zero) ?: Offset.Zero
            val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            val edge = MaterialTheme.colorScheme.primary
            Canvas(Modifier.matchParentSize()) {
                val topLeft = Offset(rect.left - origin.x, rect.top - origin.y)
                val size = Size(rect.width, rect.height)
                drawRect(fill, topLeft, size)
                drawRect(edge, topLeft, size, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}
