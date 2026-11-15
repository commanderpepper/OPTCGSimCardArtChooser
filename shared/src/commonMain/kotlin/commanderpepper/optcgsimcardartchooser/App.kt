package commanderpepper.optcgsimcardartchooser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import commanderpepper.optcgsimcardartchooser.navigation.AppNavigation
import commanderpepper.optcgsimcardartchooser.simfolder.FolderCheck
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

/** Temporary home screen until the card browser exists. */
@Composable
fun HomeScreen(folder: FolderCheck.Valid, onChangeFolder: () -> Unit, onBrowseCards: () -> Unit) {
    Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("OPTCGSim folder verified", style = MaterialTheme.typography.headlineSmall)
        Text(folder.selectedPath)
        Text("${folder.setCount} sets · ${folder.cardsPath}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBrowseCards) { Text("Browse cards") }
            OutlinedButton(onClick = onChangeFolder) { Text("Change folder") }
        }
    }
}
