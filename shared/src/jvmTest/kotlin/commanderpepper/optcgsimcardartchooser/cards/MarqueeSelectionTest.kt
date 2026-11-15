package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class MarqueeSelectionTest {

    @Test
    fun itemsTouchingFindsOverlappingBounds() {
        val bounds = mapOf("a" to Rect(0f, 0f, 100f, 100f), "b" to Rect(0f, 110f, 100f, 210f), "c" to Rect(0f, 220f, 100f, 320f))
        assertEquals(setOf("a", "b"), itemsTouching(bounds, Rect(50f, 50f, 60f, 150f)))
        assertEquals(setOf("b"), itemsTouching(bounds, Rect(10f, 120f, 20f, 130f)), "entirely inside one item")
        assertTrue(itemsTouching(bounds, Rect(200f, 0f, 300f, 300f)).isEmpty())
    }

    /** Three 100×100 items stacked with 10px gaps: a at y 0–100, b at 110–210, c at 220–320. */
    private class Harness {
        val selected = mutableListOf<Set<String>>()
        val clicked = mutableListOf<String>()
        var touchingMidDrag: Set<String> = emptySet()
        lateinit var state: MarqueeState

        val scene = ImageComposeScene(width = 300, height = 400, density = Density(1f)) {
            MarqueeSelectionArea(onSelect = { selected += it }, modifier = Modifier.fillMaxSize()) {
                state = this
                Column {
                    for (id in listOf("a", "b", "c")) {
                        Box(Modifier.size(100.dp).marqueeItem(id).clickable { clicked += id })
                        Box(Modifier.size(10.dp))
                    }
                }
            }
        }

        fun press(at: Offset) {
            scene.sendPointerEvent(PointerEventType.Move, at)
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        }

        fun moveTo(at: Offset) = scene.sendPointerEvent(PointerEventType.Move, at, buttons = PointerButtons(isPrimaryPressed = true))

        fun release(at: Offset) {
            scene.sendPointerEvent(PointerEventType.Release, at, buttons = PointerButtons(), button = PointerButton.Primary)
            scene.render()
        }
    }

    @Test
    fun draggingABoxSelectsTheItemsItTouches() {
        val h = Harness()
        h.scene.use { scene ->
            scene.render()
            h.press(Offset(50f, 40f))
            listOf(60f, 100f, 150f).forEach { y -> h.moveTo(Offset(70f, y)); scene.render() }
            h.touchingMidDrag = h.state.touching
            h.release(Offset(70f, 150f))
        }
        assertEquals(setOf("a", "b"), h.touchingMidDrag, "highlighted live while dragging")
        assertEquals(listOf(setOf("a", "b")), h.selected)
        assertTrue(h.clicked.isEmpty(), "a drag isn't a click")
    }

    @Test
    fun aPlainClickStillReachesTheItemAndSelectsNothing() {
        val h = Harness()
        h.scene.use { scene ->
            scene.render()
            h.press(Offset(50f, 270f))
            h.release(Offset(50f, 270f))
        }
        assertEquals(listOf("c"), h.clicked)
        assertTrue(h.selected.isEmpty())
    }

    @Test
    fun aBoxOverEmptySpaceSelectsNothing() {
        val h = Harness()
        h.scene.use { scene ->
            scene.render()
            h.press(Offset(150f, 20f))
            listOf(100f, 300f).forEach { y -> h.moveTo(Offset(250f, y)); scene.render() }
            h.release(Offset(250f, 300f))
        }
        assertTrue(h.selected.isEmpty(), "nothing touched, so no selection callback")
    }
}
