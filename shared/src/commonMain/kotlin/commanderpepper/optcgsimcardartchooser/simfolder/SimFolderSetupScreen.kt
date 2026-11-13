package commanderpepper.optcgsimcardartchooser.simfolder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SimFolderSetupScreen(
    state: SimFolderState,
    onPathChanged: (String) -> Unit,
    onBrowse: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Locate OPTCGSim", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Select the folder where OPTCGSim is installed. On Windows this is the folder " +
                    "that contains OPTCGSim.exe; on macOS, select OPTCGSim.app or the folder containing it.",
                style = MaterialTheme.typography.bodyMedium,
            )

            state.notice?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.pathInput,
                    onValueChange = onPathChanged,
                    label = { Text("OPTCGSim folder") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onBrowse) { Text("Browse…") }
            }

            if (state.suggestions.isNotEmpty()) {
                Text("Detected installs", style = MaterialTheme.typography.titleSmall)
                state.suggestions.forEach { suggestion ->
                    TextButton(onClick = { onPathChanged(suggestion) }) { Text(suggestion) }
                }
            }

            CheckResult(state.check)

            Button(onClick = onConfirm, enabled = state.canConfirm, modifier = Modifier.align(Alignment.End)) {
                Text("Use this folder")
            }
        }
    }
}

@Composable
private fun CheckResult(check: FolderCheck?) {
    when (check) {
        null -> Unit
        is FolderCheck.Invalid -> Text(check.reason, color = MaterialTheme.colorScheme.error)
        is FolderCheck.Valid -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Found card art for ${check.setCount} sets.", color = MaterialTheme.colorScheme.primary)
            Text("Card folder: ${check.cardsPath}", style = MaterialTheme.typography.bodySmall)
            if (!check.writable) {
                Text(
                    "This app can't write to that folder. Move OPTCGSim somewhere your user account " +
                        "can modify, or adjust the folder's permissions.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
