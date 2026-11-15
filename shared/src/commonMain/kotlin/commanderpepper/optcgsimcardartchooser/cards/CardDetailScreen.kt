package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Full card proportions (the sim's art is 480x671). */
private val ArtSize = DpSize(180.dp, 252.dp)

/**
 * One card's art choices. Clicking an option applies it to the sim immediately; the option in
 * use is outlined and labelled "In use".
 */
@Composable
fun CardDetailScreen(
    state: CardDetailState,
    onSelect: (optionId: String) -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }
            val card = state.card
            Text(
                if (card != null) "${card.name} · ${card.id}" else "Card",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
            else -> {
                Text(
                    if (state.canApply) "Choose the art the sim should use. It's copied into the sim folder right away."
                    else "Verify the OPTCGSim folder first; art can't be applied without it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.message?.let { MessageBanner(it, onDismissMessage) }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = ArtSize.width + 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.options, key = { it.id }) { option ->
                        ArtOptionTile(
                            option = option,
                            isApplied = option.id == state.appliedId,
                            isApplying = option.id == state.applyingId,
                            enabled = state.canApply && state.applyingId == null && option.image !is ArtImage.Unavailable,
                            onClick = { onSelect(option.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtOptionTile(
    option: ArtOption,
    isApplied: Boolean,
    isApplying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // The applied tile never looks disabled, even while another option is being applied.
    val looksEnabled = enabled || isApplied
    val border = if (isApplied) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else CardDefaults.outlinedCardBorder(looksEnabled)
    OutlinedCard(onClick = onClick, enabled = looksEnabled, border = border) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val frame = Modifier.size(ArtSize).clip(RoundedCornerShape(8.dp))
                when (val image = option.image) {
                    is ArtImage.Loaded -> Image(image.bitmap, contentDescription = option.title, contentScale = ContentScale.Crop, modifier = frame)
                    ArtImage.Loading -> Box(frame.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                    ArtImage.Unavailable -> ImageNotFoundPlaceholder(frame)
                }
                if (isApplying) {
                    Box(frame.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(option.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (isApplied) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
                        Text(
                            "In use",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                option.subtitle ?: option.variant?.id.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MessageBanner(message: String, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Button(onClick = onDismiss) { Text("OK") }
        }
    }
}
