package commanderpepper.optcgsimcardartchooser.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import commanderpepper.optcgsimcardartchooser.simfolder.FolderCheck
import commanderpepper.optcgsimcardartchooser.theme.TextScale
import commanderpepper.optcgsimcardartchooser.theme.TextSizeActions
import commanderpepper.optcgsimcardartchooser.theme.ThemeMode

@Composable
fun HomeScreen(
    folder: FolderCheck.Valid,
    state: HomeState,
    onBrowseCards: () -> Unit,
    onChangeFolder: () -> Unit,
    onReapply: () -> Unit,
    onDismissMessage: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    textScale: Int,
    textSizeActions: TextSizeActions,
) {
    Column(
        // Scrolls so the About section stays reachable in small windows or at larger text sizes.
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(32.dp).widthIn(max = 760.dp),
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        ThemePicker(themeMode, onThemeModeChange)
        TextSizePicker(textScale, textSizeActions)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        About()
    }
}

/** What the app does, where its data comes from, how to undo changes, and the fan-app disclaimer. */
@Composable
private fun About() {
    Text("About", style = MaterialTheme.typography.titleMedium)
    Text(
        "OPTCGSim Card Art Chooser lets you pick which art OPTCGSim shows for each card: an alternate " +
            "art or an image of your own. It works by replacing card images in your OPTCGSim folder.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "Card details and alternate art come from OPTCGAPI (optcgapi.com).",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "The sim's original image is backed up before a card is changed. To put it back, choose " +
            "\"Sim original\" on the card's page, or use \"Restore originals\" in Browse cards.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "OPTCGSim Card Art Chooser is an unofficial fan-made app and is not affiliated with, sponsored by, " +
            "or endorsed by Bandai, Eiichirō Oda, Shueisha, or Toei Animation. " +
            "One Piece and all related content are © Eiichirō Oda/Shueisha, Toei Animation.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "It is also an independent project and is not affiliated with, sponsored by, or endorsed by " +
            "the makers of OPTCGSim or OPTCGAPI.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "Text size  [A−] 110% [A+]  Reset", limited to [TextScale.MIN]–[TextScale.MAX]. */
@Composable
private fun TextSizePicker(percent: Int, actions: TextSizeActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Text size", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = actions.onDecrease, enabled = percent > TextScale.MIN) { Text("A−") }
        Text(
            "$percent%",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 48.dp),
        )
        OutlinedButton(onClick = actions.onIncrease, enabled = percent < TextScale.MAX) { Text("A+") }
        TextButton(onClick = actions.onReset, enabled = percent != TextScale.DEFAULT) { Text("Reset") }
    }
}

/** System / Light / Dark, as one segmented control. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val modes = ThemeMode.entries
    SingleChoiceSegmentedButtonRow {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            ) { Text(mode.label) }
        }
    }
}
