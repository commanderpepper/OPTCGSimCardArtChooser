package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardColor
import commanderpepper.optcgsimcardartchooser.catalog.model.Category

/**
 * Card browser: search, Color / Card type / Set dropdown filters, and a grid of card tiles with
 * thumbnails. Clicking a card opens its art options. Still to come: more filter dropdowns (traits,
 * alt art — already supported by the ViewModel).
 */
@Composable
fun CardBrowserScreen(
    state: CardBrowserState,
    onQueryChanged: (String) -> Unit,
    onToggleColor: (CardColor) -> Unit,
    onToggleCategory: (Category) -> Unit,
    onToggleSet: (String) -> Unit,
    onToggleArtStatus: (ArtStatus) -> Unit,
    deckActions: DeckListActions,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    loadThumbnail: suspend (Card) -> ImageBitmap?,
    onCardSelected: (cardId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Cards", style = MaterialTheme.typography.headlineSmall)
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text("Retry") }
            }
            else -> {
                Filters(state, onQueryChanged, onToggleColor, onToggleCategory, onToggleSet, onToggleArtStatus, deckActions, onClearFilters)
                state.deckMessage?.let { DeckMessageBanner(it, deckActions.onDismissMessage) }
                Text(
                    "${state.cards.size} of ${state.totalCards} cards",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.cards.isEmpty()) {
                    Text("No cards match these filters.", modifier = Modifier.padding(top = 24.dp))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 280.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.cards, key = { it.id }) { card ->
                            CardTile(card, isModified = card.id in state.modifiedCardIds, loadThumbnail, onClick = { onCardSelected(card.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Filters(
    state: CardBrowserState,
    onQueryChanged: (String) -> Unit,
    onToggleColor: (CardColor) -> Unit,
    onToggleCategory: (Category) -> Unit,
    onToggleSet: (String) -> Unit,
    onToggleArtStatus: (ArtStatus) -> Unit,
    deckActions: DeckListActions,
    onClearFilters: () -> Unit,
) {
    val filter = state.filter
    OutlinedTextField(
        value = filter.query,
        onValueChange = onQueryChanged,
        label = { Text("Search by name or card number") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        MultiSelectDropdown(
            label = "Color",
            sections = listOf(DropdownSection(null, CardColor.entries)),
            selected = filter.colors,
            optionLabel = { it.name },
            onToggle = onToggleColor,
        )
        MultiSelectDropdown(
            label = "Card type",
            sections = listOf(DropdownSection(null, Category.entries)),
            selected = filter.categories,
            optionLabel = { it.name },
            onToggle = onToggleCategory,
        )
        MultiSelectDropdown(
            label = "Set",
            sections = state.sets.groupedByProductLine().map { (group, sets) -> DropdownSection(group.label, sets.map { it.id }) },
            selected = filter.setIds,
            optionLabel = { id -> state.sets.firstOrNull { it.id == id }?.let { "${it.id} · ${it.name}" } ?: id },
            summaryLabel = { it },
            onToggle = onToggleSet,
        )
        MultiSelectDropdown(
            label = "Art",
            sections = listOf(DropdownSection(null, ArtStatus.entries)),
            selected = state.artStatuses,
            optionLabel = { it.label },
            onToggle = onToggleArtStatus,
        )
        DeckListButton(state, deckActions)
        if (state.hasActiveFilter) {
            TextButton(onClick = onClearFilters) { Text("Clear filters") }
        }
    }
}

/** Callbacks for the deck list filter. */
class DeckListActions(
    val onApply: (String) -> Unit,
    val onClear: () -> Unit,
    val onDismissMessage: () -> Unit,
)

/** "Deck list…" when inactive; "Deck: 13 cards" plus a clear button when a list is applied. */
@Composable
private fun DeckListButton(state: CardBrowserState, actions: DeckListActions) {
    var editing by remember { mutableStateOf(false) }
    val deck = state.deckCardIds
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { editing = true }) {
            Text(if (deck == null) "Deck list…" else "Deck: ${deck.size} card${if (deck.size == 1) "" else "s"}")
        }
        if (deck != null) {
            TextButton(onClick = actions.onClear) { Text("✕") }
        }
    }
    if (editing) {
        DeckListDialog(
            initialText = state.deckText,
            onApply = { text ->
                actions.onApply(text)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun DeckListDialog(initialText: String, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by deck list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste a deck list, one card per line. Quantities are ignored.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("1xOP09-061\n4xEB05-033\n4xOP09-069") },
                    minLines = 10,
                    maxLines = 16,
                    modifier = Modifier.fillMaxWidth().widthIn(min = 360.dp),
                )
            }
        },
        confirmButton = { Button(onClick = { onApply(text) }, enabled = text.isNotBlank()) { Text("Show cards") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeckMessageBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

/** A group of dropdown options with an optional header (e.g. "Starter Decks"). */
private class DropdownSection<T>(val title: String?, val options: List<T>)

/**
 * A button showing the current selection ("Color: Red, Blue", "Set: 5 selected" or "Color: All")
 * that opens a checkbox menu. The menu stays open while toggling so several options can be
 * picked at once; long menus scroll.
 */
@Composable
private fun <T> MultiSelectDropdown(
    label: String,
    sections: List<DropdownSection<T>>,
    selected: Set<T>,
    optionLabel: (T) -> String,
    onToggle: (T) -> Unit,
    summaryLabel: (T) -> String = optionLabel,
) {
    var expanded by remember { mutableStateOf(false) }
    val chosen = sections.flatMap { it.options }.filter { it in selected }
    val summary = when {
        chosen.isEmpty() -> "All"
        chosen.size > MAX_SUMMARY_ITEMS -> "${chosen.size} selected"
        else -> chosen.joinToString(", ", transform = summaryLabel)
    }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$label: $summary", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(" ▾")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 480.dp),
        ) {
            sections.forEach { section ->
                section.title?.let { title ->
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
                    )
                }
                section.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        leadingIcon = { Checkbox(checked = option in selected, onCheckedChange = null) },
                        onClick = { onToggle(option) },
                    )
                }
            }
        }
    }
}

private const val MAX_SUMMARY_ITEMS = 3

@Composable
private fun CardTile(card: Card, isModified: Boolean, loadThumbnail: suspend (Card) -> ImageBitmap?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardThumbnailImage(card = card, load = loadThumbnail)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(card.id, style = MaterialTheme.typography.labelMedium)
                    if (isModified) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
                            Text(
                                "Modified",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Text(card.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${card.category.name} · ${card.colors.joinToString("/") { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                )
                val alts = card.variants.size - 1
                Text(
                    if (alts > 0) "$alts alternate art${if (alts > 1) "s" else ""}" else "Regular art only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
