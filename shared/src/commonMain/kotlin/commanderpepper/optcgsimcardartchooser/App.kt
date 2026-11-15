package commanderpepper.optcgsimcardartchooser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import commanderpepper.optcgsimcardartchooser.navigation.AppNavigation
import commanderpepper.optcgsimcardartchooser.simfolder.FolderPicker
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** Requires [dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory] to be provided by the caller. */
@Composable
fun App(
    folderPicker: FolderPicker,
    folderViewModel: SimFolderViewModel = metroViewModel(),
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNavigation(folderViewModel = folderViewModel, folderPicker = folderPicker)
        }
    }
}
