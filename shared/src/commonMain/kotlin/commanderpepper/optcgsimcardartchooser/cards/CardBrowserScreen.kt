package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import commanderpepper.optcgsimcardartchooser.catalog.alternateArtCount
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardColor
import commanderpepper.optcgsimcardartchooser.catalog.model.Category

/**
 * Card browser: search, dropdown filters (color, card type, set, trait, art status, alt art or
 * custom image, deck list), and a grid of card tiles with thumbnails. Clicking a card opens its
 * art options.
 */
@Composable
fun CardBrowserScreen(
    state: CardBrowserState,
    onQueryChanged: (String) -> Unit,
    onToggleColor: (CardColor) -> Unit,
    onToggleCategory: (Category) -> Unit,
    onToggleSet: (String) -> Unit,
    onToggleTrait: (String) -> Unit,
    onToggleArtStatus: (ArtStatus) -> Unit,
    onToggleArtExtra: (ArtExtra) -> Unit,
    deckActions: DeckListActions,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    loadThumbnail: suspend (Card) -> ImageBitmap?,
    onCardSelected: (cardId: String) -> Unit,
    /** Long press or right-click on a card: open the quick-swap window. */
    onCardLongPressed: (cardId: String) -> Unit,
    selection: SelectionActions,
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
                Filters(state, onQueryChanged, onToggleColor, onToggleCategory, onToggleSet, onToggleTrait, onToggleArtStatus, onToggleArtExtra, deckActions, onClearFilters, selection)
                state.deckMessage?.let { DeckMessageBanner(it, deckActions.onDismissMessage) }
                Text(
                    "${state.cards.size} of ${state.totalCards} cards · right-click or long-press a card to swap its art quickly · " +
                        "tick cards or drag a box around them to change several at once",
                    style = MaterialTheme.typography.bodySmall,
                )
                SelectionBar(state, selection)
                if (state.cards.isEmpty()) {
                    Text("No cards match these filters.", modifier = Modifier.padding(top = 24.dp))
                } else {
                    // Drag across cards to box-select them; the hits are added to the selection.
                    MarqueeSelectionArea(onSelect = selection.onSelectMany, modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 280.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.cards, key = { it.id }) { card ->
                                CardTile(
                                    card = card,
                                    modifier = Modifier.marqueeItem(card.id),
                                    isModified = card.id in state.modifiedCardIds,
                                    customImages = state.customImageCounts[card.id] ?: 0,
                                    isSelected = card.id in state.selectedCardIds || card.id in touching,
                                    thumbnailRevision = state.thumbnailRevision,
                                    loadThumbnail = loadThumbnail,
                                    onClick = { onCardSelected(card.id) },
                                    onLongClick = { onCardLongPressed(card.id) },
                                    onToggleSelected = { selection.onToggle(card.id) },
                                )
                            }
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
    onToggleTrait: (String) -> Unit,
    onToggleArtStatus: (ArtStatus) -> Unit,
    onToggleArtExtra: (ArtExtra) -> Unit,
    deckActions: DeckListActions,
    onClearFilters: () -> Unit,
    selection: SelectionActions,
) {
    val filter = state.filter
    OutlinedTextField(
        value = filter.query,
        onValueChange = onQueryChanged,
        label = { Text("Search by name, card number or trait") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
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
                searchPlaceholder = "Search sets",
            )
            MultiSelectDropdown(
                label = "Trait",
                sections = listOf(DropdownSection(null, state.traits)),
                selected = filter.traits,
                optionLabel = { it },
                onToggle = onToggleTrait,
                searchPlaceholder = "Search traits",
            )
            MultiSelectDropdown(
                label = "Art",
                sections = listOf(DropdownSection(null, ArtStatus.entries)),
                selected = state.artStatuses,
                optionLabel = { it.label },
                onToggle = onToggleArtStatus,
            )
            MultiSelectDropdown(
                label = "Has",
                sections = listOf(DropdownSection(null, ArtExtra.entries)),
                selected = state.artExtras,
                optionLabel = { it.label },
                onToggle = onToggleArtExtra,
            )
            DeckListButton(state, deckActions)
            if (state.hasActiveFilter) {
                TextButton(onClick = onClearFilters) { Text("Clear filters") }
            }
        }
        // Right edge: select (or deselect) every card the current filters show.
        OutlinedButton(onClick = if (state.allShownSelected) selection.onDeselectAllShown else selection.onSelectAllShown, enabled = state.cards.isNotEmpty()) {
            Text(if (state.allShownSelected) "Deselect all" else "Select all")
        }
    }
}

/** Callbacks for selecting cards and changing their art in bulk. */
class SelectionActions(
    val onToggle: (cardId: String) -> Unit,
    /** Cards box-selected by dragging; added to the selection. */
    val onSelectMany: (Set<String>) -> Unit,
    val onSelectAllShown: () -> Unit,
    /** Removes the cards the current filters show from the selection. */
    val onDeselectAllShown: () -> Unit,
    val onClear: () -> Unit,
    val onUseAltArt: () -> Unit,
    val onUseSp: () -> Unit,
    /** Each card's rarest art: Manga, else SP, else Alt Art. */
    val onUseMaxRarity: () -> Unit,
    val onRestoreOriginals: () -> Unit,
    val onDismissMessage: () -> Unit,
)

/**
 * Shown once cards are selected: the count, clear, and — only with two or more selected — the
 * bulk actions ("Select all" lives at the right of the filter row). Also shows progress and the
 * summary of the last bulk action.
 */
@Composable
private fun SelectionBar(state: CardBrowserState, actions: SelectionActions) {
    val count = state.selectedCardIds.size
    val progress = state.bulkProgress
    if (count == 0 && progress == null && state.bulkMessage == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (progress != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Changing art… ${progress.first} of ${progress.second}", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (count > 0) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$count selected", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = actions.onClear) { Text("Clear selection") }
                    if (state.canBulkChange) {
                        Button(onClick = actions.onUseAltArt) { Text("Use alt art") }
                        Button(onClick = actions.onUseSp) { Text("Use SP") }
                        Button(onClick = actions.onUseMaxRarity) { Text("Use max rarity") }
                        OutlinedButton(onClick = actions.onRestoreOriginals) { Text("Restore originals") }
                    } else {
                        Text("Select another card to change several at once.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.bulkMessage?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = actions.onDismissMessage) { Text("OK") }
                }
            }
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
internal class DropdownSection<T>(val title: String?, val options: List<T>)

/** Options whose label contains [search] (case-insensitive); empty sections are dropped. Blank = all. */
internal fun <T> List<DropdownSection<T>>.narrowedTo(search: String, label: (T) -> String): List<DropdownSection<T>> {
    val text = search.trim()
    if (text.isEmpty()) return this
    return map { s -> DropdownSection(s.title, s.options.filter { label(it).contains(text, ignoreCase = true) }) }
        .filter { it.options.isNotEmpty() }
}

/**
 * A button showing the current selection ("Color: Red, Blue", "Set: 5 selected" or "Color: All")
 * that opens a checkbox menu. The menu stays open while toggling so several options can be
 * picked at once; long menus scroll. With [searchPlaceholder], the menu starts with a search box
 * (focused on open, cleared on close) that narrows the options as you type.
 */
@Composable
private fun <T> MultiSelectDropdown(
    label: String,
    sections: List<DropdownSection<T>>,
    selected: Set<T>,
    optionLabel: (T) -> String,
    onToggle: (T) -> Unit,
    summaryLabel: (T) -> String = optionLabel,
    searchPlaceholder: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val chosen = sections.flatMap { it.options }.filter { it in selected }
    val summary = when {
        chosen.isEmpty() -> "All"
        chosen.size > MAX_SUMMARY_ITEMS -> "${chosen.size} selected"
        else -> chosen.joinToString(", ", transform = summaryLabel)
    }
    val visibleSections = sections.narrowedTo(search, optionLabel)
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$label: $summary", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(" ▾")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                search = ""
            },
            modifier = Modifier.heightIn(max = 480.dp),
        ) {
            if (searchPlaceholder != null) {
                val focus = remember { FocusRequester() }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(searchPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(min = 260.dp).focusRequester(focus),
                )
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                if (visibleSections.isEmpty()) {
                    Text(
                        "No ${label.lowercase()}s match \"${search.trim()}\"",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            visibleSections.forEach { section ->
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
private fun CardTile(
    card: Card,
    modifier: Modifier = Modifier,
    isModified: Boolean,
    /** How many of the user's own images the card has. */
    customImages: Int,
    isSelected: Boolean,
    thumbnailRevision: Int,
    loadThumbnail: suspend (Card) -> ImageBitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelected: () -> Unit,
) {
    val shape = CardDefaults.shape
    Card(
        shape = shape,
        colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
        // Long press or right-click opens the quick-swap window.
        modifier = modifier.fillMaxWidth().clip(shape)
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .onSecondaryClick(onLongClick)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardThumbnailImage(card = card, load = loadThumbnail, revision = thumbnailRevision)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                // FlowRow: on narrow tiles the badge moves under the ID instead of being squeezed.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(card.id, style = MaterialTheme.typography.labelMedium)
                    if (isModified) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
                            Text(
                                "Modified",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                softWrap = false,
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
                val alts = card.alternateArtCount
                Text(
                    if (alts > 0) "$alts alternate art${if (alts > 1) "s" else ""}" else "Regular art only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (customImages > 0) {
                    Text(
                        "+ $customImages custom image${if (customImages > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
        }
    }
}
