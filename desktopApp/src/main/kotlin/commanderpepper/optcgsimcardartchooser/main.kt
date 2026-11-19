package commanderpepper.optcgsimcardartchooser

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import commanderpepper.optcgsimcardartchooser.catalog.conversion.AppFlavor
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import org.jetbrains.skia.Image

fun main() {
    val graph = createGraph<AppGraph>()
    val icon = loadAppIcon()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "OPTCGSim Card Art Chooser" + if (AppFlavor.isDebug) " (Debug)" else "",
            icon = icon,
            state = rememberWindowState(width = 960.dp, height = 720.dp),
        ) {
            CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
                App(folderPicker = graph.folderPicker, imagePicker = graph.imagePicker)
            }
        }
    }
}

/** The title bar / taskbar icon (`src/main/resources/icon.png`); null falls back to the default. */
private fun loadAppIcon(): Painter? =
    object {}.javaClass.getResourceAsStream("/icon.png")?.use { stream ->
        BitmapPainter(Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap())
    }
