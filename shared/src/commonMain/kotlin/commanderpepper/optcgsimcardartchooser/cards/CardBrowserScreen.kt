package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
 * thumbnails. Still to come: more filter dropdowns (traits, alt art — already supported by the
 * ViewModel) and opening a card to choose its art.
 */
@Composable
fun CardBrowserScreen(
    state: CardBrowserState,
    onQueryChanged: (String) -> Unit,
    onToggleColor: (CardColor) -> Unit,
    onToggleCategory: (Category) -> Unit,
    onToggleSet: (String) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    loadThumbnail: suspend (Card) -> ImageBitmap?,
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
                Filters(state, onQueryChanged, onToggleColor, onToggleCategory, onToggleSet, onClearFilters)
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
                        items(state.cards, key = { it.id }) { card -> CardTile(card, loadThumbnail) }
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
        if (state.hasActiveFilter) {
            TextButton(onClick = onClearFilters) { Text("Clear filters") }
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
private fun CardTile(card: Card, loadThumbnail: suspend (Card) -> ImageBitmap?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardThumbnailImage(card = card, load = loadThumbnail)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.id, style = MaterialTheme.typography.labelMedium)
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
