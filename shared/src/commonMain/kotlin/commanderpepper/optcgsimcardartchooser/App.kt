package commanderpepper.optcgsimcardartchooser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import commanderpepper.optcgsimcardartchooser.simfolder.FolderCheck
import commanderpepper.optcgsimcardartchooser.simfolder.FolderPicker
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderLocator
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderSetupScreen
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderStore
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderViewModel

@Composable
fun App(
    locator: SimFolderLocator,
    store: SimFolderStore,
    folderPicker: FolderPicker,
) {
    val viewModel = viewModel { SimFolderViewModel(locator, store) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val confirmed = state.confirmed
            if (confirmed == null) {
                SimFolderSetupScreen(
                    state = state,
                    onPathChanged = viewModel::onPathChanged,
                    onBrowse = { viewModel.onPicked(folderPicker.pick(state.pathInput.ifBlank { null })) },
                    onConfirm = viewModel::confirm,
                )
            } else {
                HomePlaceholder(confirmed, onChangeFolder = viewModel::changeFolder)
            }
        }
    }
}

/** Temporary home screen until the card browser exists. */
@Composable
private fun HomePlaceholder(folder: FolderCheck.Valid, onChangeFolder: () -> Unit) {
    Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("OPTCGSim folder verified", style = MaterialTheme.typography.headlineSmall)
        Text(folder.selectedPath)
        Text("${folder.setCount} sets · ${folder.cardsPath}", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onChangeFolder) { Text("Change folder") }
    }
}
