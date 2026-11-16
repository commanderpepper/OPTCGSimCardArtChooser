package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import commanderpepper.optcgsimcardartchooser.catalog.CardFilter
import commanderpepper.optcgsimcardartchooser.catalog.filteredBy
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardColor
import commanderpepper.optcgsimcardartchooser.catalog.model.CardSet
import commanderpepper.optcgsimcardartchooser.catalog.model.Catalog
import commanderpepper.optcgsimcardartchooser.catalog.model.Category
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.simfolder.SimCardsFolder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether a card shows the sim's own art or art applied by this app. */
enum class ArtStatus(val label: String) { Modified("Modified"), Original("Original") }

/** Extra art a card can be required to have; ticking several requires all of them. */
enum class ArtExtra(val label: String) { AltArt("Alt art"), CustomImage("Custom image") }

data class CardBrowserState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val filter: CardFilter = CardFilter(),
    /** Empty or both = every card. */
    val artStatuses: Set<ArtStatus> = emptySet(),
    /** Only cards with at least one of the user's own images. */
    val onlyWithCustomImage: Boolean = false,
    /** How many custom images each card has, used or not; cards without any are left out. */
    val customImageCounts: Map<String, Int> = emptyMap(),
    /** Card IDs from a pasted deck list (only ones in the catalog), in deck order; null = no deck filter. */
    val deckCardIds: List<String>? = null,
    /** The deck list as last entered, to pre-fill the dialog. */
    val deckText: String = "",
    /** Shown after applying a deck list, e.g. which cards weren't found. */
    val deckMessage: String? = null,
    /** Cards matching every filter: catalog order, or deck order when a deck list is applied. */
    val cards: List<Card> = emptyList(),
    val totalCards: Int = 0,
    /** Cards whose art was changed through the app. */
    val modifiedCardIds: Set<String> = emptySet(),
    /** Options for the set filter, ordered by product line (OP, EB, ST, PRB, P) then number. */
    val sets: List<CardSet> = emptyList(),
    /** Options for the trait filter. */
    val traits: List<String> = emptyList(),
    /** Bumped when art may have changed (e.g. after a quick swap), so visible thumbnails reload. */
    val thumbnailRevision: Int = 0,
    /** Cards ticked for a bulk action; can include cards hidden by the current filters. */
    val selectedCardIds: Set<String> = emptySet(),
    /** Set while a bulk action runs: (cards done, total). */
    val bulkProgress: Pair<Int, Int>? = null,
    /** Summary of the last bulk action. */
    val bulkMessage: String? = null,
) {
    val hasActiveFilter: Boolean
        get() = filter != CardFilter() || artStatuses.isNotEmpty() || onlyWithCustomImage || deckCardIds != null

    /** The ticked options of the "Has" filter. */
    val artExtras: Set<ArtExtra>
        get() = buildSet {
            if (filter.onlyWithAlternateArt) add(ArtExtra.AltArt)
            if (onlyWithCustomImage) add(ArtExtra.CustomImage)
        }

    /** Every card the current filters show is selected (so "Select all" becomes "Deselect all"). */
    val allShownSelected: Boolean get() = cards.isNotEmpty() && cards.all { it.id in selectedCardIds }

    /** Bulk actions are offered only when several cards are selected. */
    val canBulkChange: Boolean get() = selectedCardIds.size >= 2 && bulkProgress == null
}

@Inject
@ViewModelKey(CardBrowserViewModel::class)
@ContributesIntoMap(AppScope::class)
class CardBrowserViewModel(
    private val repository: CatalogRepository,
    private val thumbnails: CardThumbnails,
    private val art: CardArt,
    simCardsFolder: SimCardsFolder,
) : ViewModel() {

    private val _state = MutableStateFlow(CardBrowserState())
    val state: StateFlow<CardBrowserState> = _state.asStateFlow()

    private var catalog: Catalog? = null

    /** The verified sim folder's `StreamingAssets/Cards` directory, resolved when the page opens. */
    private val cardsPath: String? = simCardsFolder.cardsPath

    init {
        load()
    }

    fun retry() = load()

    /** The card's current art from the sim folder, or null to show the "image not found" placeholder. */
    suspend fun thumbnail(card: Card): ImageBitmap? = cardsPath?.let { thumbnails.load(it, card) }

    fun onQueryChanged(query: String) = updateFilter { it.copy(query = query) }

    fun toggleColor(color: CardColor) = updateFilter { it.copy(colors = it.colors.toggle(color)) }

    fun toggleCategory(category: Category) = updateFilter { it.copy(categories = it.categories.toggle(category)) }

    fun toggleSet(setId: String) = updateFilter { it.copy(setIds = it.setIds.toggle(setId)) }

    fun toggleTrait(trait: String) = updateFilter { it.copy(traits = it.traits.toggle(trait)) }

    fun toggleArtExtra(extra: ArtExtra) = when (extra) {
        ArtExtra.AltArt -> updateFilter { it.copy(onlyWithAlternateArt = !it.onlyWithAlternateArt) }
        ArtExtra.CustomImage -> _state.update { withMatches(it.copy(onlyWithCustomImage = !it.onlyWithCustomImage)) }
    }

    fun toggleArtStatus(status: ArtStatus) =
        _state.update { withMatches(it.copy(artStatuses = it.artStatuses.toggle(status))) }

    fun clearFilters() = _state.update {
        withMatches(
            it.copy(filter = CardFilter(), artStatuses = emptySet(), onlyWithCustomImage = false, deckCardIds = null, deckMessage = null),
        )
    }

    /**
     * Shows only the cards in a pasted deck list. Card numbers not in the catalog are reported
     * in [CardBrowserState.deckMessage]; if none are recognised, the current filter is kept.
     */
    fun applyDeckList(text: String) {
        val ids = DeckList.parse(text)
        val known = catalog?.cards?.mapTo(HashSet()) { it.id }.orEmpty()
        val found = ids.filter { it in known }
        val missing = ids.filterNot { it in known }
        _state.update { current ->
            when {
                ids.isEmpty() -> current.copy(deckText = text, deckMessage = "No card numbers were found in that list.")
                found.isEmpty() -> current.copy(
                    deckText = text,
                    deckMessage = "None of the ${ids.size} cards in that list are in the catalog: ${missing.joinToString(", ")}",
                )
                else -> withMatches(current.copy(deckCardIds = found, deckText = text, deckMessage = notFoundMessage(missing)))
            }
        }
    }

    fun clearDeckList() = _state.update { withMatches(it.copy(deckCardIds = null, deckMessage = null)) }

    fun dismissDeckMessage() = _state.update { it.copy(deckMessage = null) }

    private fun notFoundMessage(missing: List<String>): String? = when (missing.size) {
        0 -> null
        1 -> "1 card wasn't found: ${missing.single()}"
        else -> "${missing.size} cards weren't found: ${missing.joinToString(", ")}"
    }

    /**
     * Re-reads which cards have modified art and which have custom images. Called whenever the
     * page is shown, since both change on the card page while this one waits underneath.
     */
    fun refreshModified() {
        viewModelScope.launch {
            val modified = art.appliedVariants().keys
            val custom = art.customImageCounts()
            _state.update { withMatches(it.copy(modifiedCardIds = modified, customImageCounts = custom)) }
        }
    }

    fun toggleSelection(cardId: String) = _state.update { it.copy(selectedCardIds = it.selectedCardIds.toggle(cardId)) }

    /** Adds cards to the selection, e.g. from a box selection. */
    fun selectMany(cardIds: Collection<String>) = _state.update { it.copy(selectedCardIds = it.selectedCardIds + cardIds) }

    /** Adds every card matching the current filters to the selection. */
    fun selectAllShown() = _state.update { s -> s.copy(selectedCardIds = s.selectedCardIds + s.cards.map { it.id }) }

    /** Removes the cards the current filters show; selected cards hidden by the filters stay selected. */
    fun deselectAllShown() = _state.update { s -> s.copy(selectedCardIds = s.selectedCardIds - s.cards.map { it.id }.toSet()) }

    fun clearSelection() = _state.update { it.copy(selectedCardIds = emptySet()) }

    fun dismissBulkMessage() = _state.update { it.copy(bulkMessage = null) }

    /** Switches every selected card to its alt art; see [applyToSelected]. */
    fun applyAltArtToSelected() = applyToSelected(ArtTier.AltArt.label) { bulkTarget(it, ArtTier.AltArt) }

    /** Switches every selected card to its SP; see [applyToSelected]. */
    fun applySpToSelected() = applyToSelected(ArtTier.SP.label) { bulkTarget(it, ArtTier.SP) }

    /** Switches every selected card to the rarest art it has (Manga, else SP, else Alt Art); see [applyToSelected]. */
    fun applyMaxRarityToSelected() = applyToSelected("max rarity", missingLabel = "alt art, SP or manga art", target = ::maxRarityTarget)

    /**
     * Switches every selected card to its [target] art, never moving a card down the rarity order
     * Manga > SP > Alt Art > Regular: a card already showing a rarer art keeps it, and one already
     * showing an art of the same tier keeps that specific choice. Cards without a target, and
     * cards using one of the user's own images, are skipped; every group is listed in the summary.
     */
    private fun applyToSelected(
        label: String,
        missingLabel: String = label,
        target: (Card) -> Variant?,
    ) = runBulk("Applying $label") { path, cards, applied ->
        val changed = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        val rarer = mutableListOf<String>()
        val custom = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val failed = mutableListOf<String>()
        forEachWithProgress(cards) { card ->
            val wanted = target(card)
            val current = applied[card.id]?.let { CustomArt.resolve(card, it) }
            when {
                current != null && CustomArt.isCustom(current) -> custom += card.id
                wanted == null -> missing += card.id
                // Same tier (e.g. the second of two SPs): keep that specific choice.
                current != null && ArtTier.of(current) == ArtTier.of(wanted) -> unchanged += card.id
                current != null && ArtTier.of(current) > ArtTier.of(wanted) -> rarer += card.id
                else -> try {
                    art.apply(path, card, wanted)
                    thumbnails.invalidate(card.id)
                    changed += card.id
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed += "${card.id} (${e.message ?: "unknown error"})"
                }
            }
        }
        buildList {
            if (changed.isNotEmpty()) add("Applied $label to ${changed.size} ${plural(changed.size)}.")
            if (unchanged.isNotEmpty()) add("${unchanged.size} already had it.")
            if (rarer.isNotEmpty()) add("${rarer.size} kept a rarer art${listed(rarer)}.")
            if (custom.isNotEmpty()) add("${custom.size} kept your custom image${listed(custom)}.")
            if (missing.isNotEmpty()) add("${missing.size} ${if (missing.size == 1) "has" else "have"} no $missingLabel${listed(missing)}.")
            if (failed.isNotEmpty()) add("Couldn't change ${failed.size}${listed(failed, "; ")}")
        }.joinToString(" ").ifBlank { "Nothing to change." }
    }

    /** Puts the sim's original art back on every selected card that was changed. */
    fun restoreSelected() = runBulk("Restoring originals") { path, cards, applied ->
        val modified = cards.filter { it.id in applied }
        val restored = mutableListOf<String>()
        val failed = mutableListOf<String>()
        forEachWithProgress(modified) { card ->
            try {
                art.restoreOriginal(path, card)
                thumbnails.invalidate(card.id)
                restored += card.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += "${card.id} (${e.message ?: "unknown error"})"
            }
        }
        buildList {
            add(if (restored.isEmpty()) "None of the selected cards were changed." else "Restored the original art on ${restored.size} ${plural(restored.size)}.")
            if (failed.isNotEmpty()) add("Couldn't restore ${failed.size}${listed(failed, "; ")}")
        }.joinToString(" ")
    }

    /**
     * Runs a bulk change on the selected cards, one at a time (missing art may need downloading),
     * then refreshes badges and thumbnails and clears the selection. [action] returns the summary.
     */
    private fun runBulk(
        verb: String,
        action: suspend BulkScope.(cardsPath: String, cards: List<Card>, applied: Map<String, String>) -> String,
    ) {
        val current = _state.value
        if (!current.canBulkChange) return
        val path = cardsPath
        if (path == null) {
            _state.update { it.copy(bulkMessage = "The OPTCGSim folder isn't verified, so art can't be changed.") }
            return
        }
        val byId = catalog?.cards?.associateBy { it.id }.orEmpty()
        val cards = current.selectedCardIds.mapNotNull(byId::get)
        _state.update { it.copy(bulkProgress = 0 to cards.size, bulkMessage = null) }
        viewModelScope.launch {
            val scope = BulkScope()
            val summary = try {
                scope.action(path, cards, art.appliedVariants())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "$verb failed: ${e.message ?: "unknown error"}"
            }
            val modified = art.appliedVariants().keys
            _state.update {
                withMatches(
                    it.copy(
                        bulkProgress = null,
                        bulkMessage = summary,
                        selectedCardIds = emptySet(),
                        modifiedCardIds = modified,
                        thumbnailRevision = it.thumbnailRevision + 1,
                    ),
                )
            }
        }
    }

    /** Helpers available inside a bulk action. */
    private inner class BulkScope {
        suspend fun forEachWithProgress(cards: List<Card>, each: suspend (Card) -> Unit) {
            _state.update { it.copy(bulkProgress = 0 to cards.size) }
            cards.forEachIndexed { index, card ->
                each(card)
                _state.update { it.copy(bulkProgress = index + 1 to cards.size) }
            }
        }
    }

    private fun plural(n: Int) = if (n == 1) "card" else "cards"

    /** ": OP01-001, OP01-002" for short lists; nothing for long ones, so the summary shows just the count. */
    private fun listed(items: List<String>, separator: String = ", "): String =
        if (items.size > MAX_LISTED_IN_SUMMARY) "" else ": " + items.joinToString(separator)

    /** After the quick-swap window closes: refresh "Modified" badges and the swapped card's thumbnail. */
    fun onQuickSwapClosed() {
        refreshModified()
        _state.update { it.copy(thumbnailRevision = it.thumbnailRevision + 1) }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val loaded = repository.load()
                val modified = art.appliedVariants().keys
                val custom = art.customImageCounts()
                catalog = loaded
                _state.update {
                    withMatches(
                        it.copy(
                            isLoading = false,
                            totalCards = loaded.cards.size,
                            sets = loaded.sets.sortedByProductLine(),
                            traits = loaded.allTraits(),
                            modifiedCardIds = modified,
                            customImageCounts = custom,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load the card catalog") }
            }
        }
    }

    private fun updateFilter(change: (CardFilter) -> CardFilter) =
        _state.update { withMatches(it.copy(filter = change(it.filter))) }

    /** Recomputes [CardBrowserState.cards] from the catalog, the deck list, the card filter and the art filters. */
    private fun withMatches(state: CardBrowserState): CardBrowserState {
        val all = catalog?.cards.orEmpty()
        val pool = state.deckCardIds?.let { deck ->
            val byId = all.associateBy { it.id }
            deck.mapNotNull(byId::get)
        } ?: all
        val cards = pool.filteredBy(state.filter)
        val statuses = state.artStatuses
        val matching = if (statuses.isEmpty() || statuses.size == ArtStatus.entries.size) {
            cards
        } else {
            val wantModified = ArtStatus.Modified in statuses
            cards.filter { (it.id in state.modifiedCardIds) == wantModified }
        }.let { shown ->
            if (state.onlyWithCustomImage) shown.filter { it.id in state.customImageCounts } else shown
        }
        return state.copy(cards = matching)
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    private companion object {
        /** Bulk summaries list card numbers only up to this many per group; beyond it, just the count. */
        const val MAX_LISTED_IN_SUMMARY = 20
    }
}
