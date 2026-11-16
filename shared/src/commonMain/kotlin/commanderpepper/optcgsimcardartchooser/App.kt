package commanderpepper.optcgsimcardartchooser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import commanderpepper.optcgsimcardartchooser.cards.ImagePicker
import commanderpepper.optcgsimcardartchooser.navigation.AppNavigation
import commanderpepper.optcgsimcardartchooser.simfolder.FolderPicker
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderViewModel
import commanderpepper.optcgsimcardartchooser.theme.AppTheme
import commanderpepper.optcgsimcardartchooser.theme.ThemeViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** Requires [dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory] to be provided by the caller. */
@Composable
fun App(
    folderPicker: FolderPicker,
    imagePicker: ImagePicker,
    folderViewModel: SimFolderViewModel = metroViewModel(),
    themeViewModel: ThemeViewModel = metroViewModel(),
) {
    val themeMode by themeViewModel.mode.collectAsStateWithLifecycle()
    AppTheme(themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNavigation(
                folderViewModel = folderViewModel,
                folderPicker = folderPicker,
                imagePicker = imagePicker,
                themeMode = themeMode,
                onThemeModeChange = themeViewModel::setMode,
            )
        }
    }
}
