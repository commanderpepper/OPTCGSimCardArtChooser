package commanderpepper.optcgsimcardartchooser.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import commanderpepper.optcgsimcardartchooser.simfolder.FolderCheck

@Composable
fun HomeScreen(
    folder: FolderCheck.Valid,
    state: HomeState,
    onBrowseCards: () -> Unit,
    onChangeFolder: () -> Unit,
    onReapply: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(32.dp).widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("OPTCGSim folder verified", style = MaterialTheme.typography.headlineSmall)
        Text(folder.selectedPath)
        Text("${folder.setCount} sets · ${folder.cardsPath}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBrowseCards) { Text("Browse cards") }
            OutlinedButton(onClick = onChangeFolder) { Text("Change folder") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Modified cards", style = MaterialTheme.typography.titleMedium)
        Text(
            when (state.modifiedCount) {
                0 -> "You haven't changed any card art yet."
                1 -> "1 card uses art chosen in this app."
                else -> "${state.modifiedCount} cards use art chosen in this app."
            } + " Sim updates replace that art; reapply it afterwards.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onReapply, enabled = state.modifiedCount > 0 && !state.isReapplying) {
                Text("Reapply modified cards")
            }
            state.progress?.let { (done, total) ->
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("$done of $total", style = MaterialTheme.typography.bodyMedium)
            }
        }
        state.message?.let { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissMessage) { Text("OK") }
                }
            }
        }
    }
}
