package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

private val QuickTileSize = DpSize(112.dp, 157.dp)

/**
 * A small window for swapping a card's art without leaving the browser: every option the card
 * page offers (sim original, catalog variants, custom images), minus adding new custom images.
 * Clicking an option applies it at once; the window stays open so options can be compared.
 *
 * Uses the card page's ViewModel, in a store owned by this window, so it's discarded (with its
 * decoded previews) when the window closes.
 */
@Composable
fun QuickArtPickerDialog(cardId: String, onDismiss: () -> Unit, onOpenCardPage: () -> Unit) {
    val store = remember(cardId) { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    val owner = remember(store) { object : ViewModelStoreOwner { override val viewModelStore = store } }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            val viewModel = assistedMetroViewModel<CardDetailViewModel, CardDetailViewModel.Factory> { create(cardId) }
            val state by viewModel.state.collectAsStateWithLifecycle()
            QuickArtPicker(state, onSelect = viewModel::select, onClose = onDismiss, onOpenCardPage = onOpenCardPage)
        }
    }
}

@Composable
fun QuickArtPicker(
    state: CardDetailState,
    onSelect: (optionId: String) -> Unit,
    onClose: () -> Unit,
    onOpenCardPage: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, shadowElevation = 8.dp, modifier = Modifier.widthIn(max = 760.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val card = state.card
                Text(
                    if (card != null) "${card.name} · ${card.id}" else "Card art",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenCardPage) { Text("Open card page") }
                TextButton(onClick = onClose) { Text("Close") }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                else -> {
                    Text(
                        state.message ?: if (state.canApply) "Click an art to use it in the sim." else "Verify the OPTCGSim folder first to change art.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    ) {
                        // Every option, custom images included; adding new ones happens on the card page.
                        state.options.forEach { option ->
                            QuickArtTile(
                                option = option,
                                isApplied = option.id == state.appliedId,
                                isApplying = option.id == state.applyingId,
                                enabled = state.canApply && state.isIdle && option.image !is ArtImage.Unavailable,
                                onClick = { onSelect(option.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickArtTile(option: ArtOption, isApplied: Boolean, isApplying: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(modifier = Modifier.width(QuickTileSize.width), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(QuickTileSize)
                .clip(shape)
                .then(if (isApplied) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (val image = option.image) {
                is ArtImage.Loaded -> Image(image.bitmap, contentDescription = option.title, contentScale = ContentScale.Crop, modifier = Modifier.size(QuickTileSize))
                ArtImage.Loading -> Box(Modifier.size(QuickTileSize).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                ArtImage.Unavailable -> ImageNotFoundPlaceholder(Modifier.size(QuickTileSize))
            }
            if (isApplying) {
                Box(Modifier.size(QuickTileSize).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(option.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (isApplied) {
            Text("In use", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
