package commanderpepper.optcgsimcardartchooser

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

fun main() {
    val graph = createGraph<AppGraph>()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "OPTCGSim Card Art Chooser",
            state = rememberWindowState(width = 960.dp, height = 720.dp),
        ) {
            CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory) {
                App(folderPicker = graph.folderPicker, imagePicker = graph.imagePicker)
            }
        }
    }
}
