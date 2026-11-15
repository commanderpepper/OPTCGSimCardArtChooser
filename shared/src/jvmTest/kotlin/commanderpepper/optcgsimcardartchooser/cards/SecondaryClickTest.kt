package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class SecondaryClickTest {

    private class Clicks {
        var right = 0
        var left = 0
    }

    private fun scene(clicks: Clicks) = ImageComposeScene(width = 200, height = 200, density = Density(1f)) {
        // Same order as the card tiles: right-click handler, then the normal click.
        Box(Modifier.size(200.dp).onSecondaryClick { clicks.right++ }.clickable { clicks.left++ })
    }

    private fun ImageComposeScene.click(button: PointerButton, buttons: PointerButtons) {
        val at = Offset(100f, 100f)
        sendPointerEvent(PointerEventType.Move, at)
        sendPointerEvent(PointerEventType.Press, at, buttons = buttons, button = button)
        sendPointerEvent(PointerEventType.Release, at, buttons = PointerButtons(), button = button)
        render()
    }

    @Test
    fun rightClickCallsTheHandlerAndNotTheNormalClick() {
        val clicks = Clicks()
        scene(clicks).use { scene ->
            scene.render()
            scene.click(PointerButton.Secondary, PointerButtons(isSecondaryPressed = true))
        }
        assertEquals(1, clicks.right)
        assertEquals(0, clicks.left)
    }

    @Test
    fun leftClickIsAnOrdinaryClick() {
        val clicks = Clicks()
        scene(clicks).use { scene ->
            scene.render()
            scene.click(PointerButton.Primary, PointerButtons(isPrimaryPressed = true))
        }
        assertEquals(0, clicks.right)
        assertEquals(1, clicks.left)
    }
}
