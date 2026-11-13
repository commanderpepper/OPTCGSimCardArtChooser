package commanderpepper.optcgsimcardartchooser

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import commanderpepper.optcgsimcardartchooser.simfolder.JvmSimFolderLocator
import commanderpepper.optcgsimcardartchooser.simfolder.NativeFolderPicker
import commanderpepper.optcgsimcardartchooser.simfolder.PreferencesSimFolderStore

fun main() {
    val locator = JvmSimFolderLocator.forCurrentHost()
    val store = PreferencesSimFolderStore()
    val picker = NativeFolderPicker()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "OPTCGSim Card Art Chooser",
            state = rememberWindowState(width = 960.dp, height = 720.dp),
        ) {
            App(locator = locator, store = store, folderPicker = picker)
        }
    }
}
