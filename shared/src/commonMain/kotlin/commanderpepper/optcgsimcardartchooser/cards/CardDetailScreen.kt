package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Callbacks for the user's own images on the card page. */
class CustomImageActions(
    /** Opens the picker to add one or more images. */
    val onAdd: () -> Unit,
    /** Images dropped onto the page. */
    val onDrop: (List<String>) -> Unit,
    /** Something was dropped but couldn't be read as images. */
    val onDropFailed: () -> Unit,
    /** Opens the picker to replace the custom image with this option ID. */
    val onReplace: (optionId: String) -> Unit,
    val onRemove: (optionId: String) -> Unit,
)

/**
 * One card's art choices. Clicking an option applies it to the sim immediately; the option in
 * use is outlined and labelled "In use". JPG, PNG or WebP files dropped anywhere on the page are added as
 * custom images.
 */
@Composable
fun CardDetailScreen(
    state: CardDetailState,
    onSelect: (optionId: String) -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    customImages: CustomImageActions,
    modifier: Modifier = Modifier,
) {
    var dropHover by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.fillMaxSize().fileDropTarget(
            enabled = state.card != null && state.isIdle,
            onHoverChange = { dropHover = it },
            onDrop = customImages.onDrop,
            onDropFailed = customImages.onDropFailed,
        ),
    ) {
        CardDetailContent(state, onSelect, onDismissMessage, onBack, customImages)
        if (dropHover) DropOverlay()
    }
}

@Composable
private fun CardDetailContent(
    state: CardDetailState,
    onSelect: (optionId: String) -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    customImages: CustomImageActions,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            enabled = state.canApply && state.isIdle && option.image !is ArtImage.Unavailable,
                            onClick = { onSelect(option.id) },
                            customActions = if (option.isCustom) {
                                {
                                    CustomImageTileActions(
                                        enabled = state.isIdle,
                                        onReplace = { customImages.onReplace(option.id) },
                                        onRemove = { customImages.onRemove(option.id) },
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                    item(key = "add-custom") {
                        AddCustomImageTile(
                            hasCustomImages = state.hasCustomImages,
                            isWorking = state.isAddingImages,
                            enabled = state.isIdle,
                            onClick = customImages.onAdd,
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
    customActions: (@Composable () -> Unit)? = null,
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
            customActions?.invoke()
        }
    }
}

@Composable
private fun CustomImageTileActions(enabled: Boolean, onReplace: () -> Unit, onRemove: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onReplace, enabled = enabled) { Text("Replace…") }
        TextButton(onClick = onRemove, enabled = enabled) { Text("Remove") }
    }
}

/** Shown while files are dragged over the page. */
@Composable
private fun DropOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
            .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Drop JPG, PNG or WebP images to add them", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Each one becomes a custom art option for this card.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** Dashed-style tile for adding the user's own images. */
@Composable
private fun AddCustomImageTile(hasCustomImages: Boolean, isWorking: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = enabled) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(ArtSize)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (isWorking) {
                    CircularProgressIndicator()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("+", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                        Text(if (hasCustomImages) "Add another image" else "Use your own image", style = MaterialTheme.typography.titleSmall)
                        Text("JPG, PNG or WebP", style = MaterialTheme.typography.bodySmall)
                        Text("or drop images on this page", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                "Resized to the sim's card size; cropped to fit if the shape differs.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
