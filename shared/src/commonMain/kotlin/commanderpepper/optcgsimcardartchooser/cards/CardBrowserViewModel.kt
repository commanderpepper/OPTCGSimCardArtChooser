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

data class CardBrowserState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val filter: CardFilter = CardFilter(),
    /** Empty or both = every card. */
    val artStatuses: Set<ArtStatus> = emptySet(),
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
) {
    val hasActiveFilter: Boolean get() = filter != CardFilter() || artStatuses.isNotEmpty() || deckCardIds != null
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

    fun setOnlyWithAlternateArt(enabled: Boolean) = updateFilter { it.copy(onlyWithAlternateArt = enabled) }

    fun toggleArtStatus(status: ArtStatus) =
        _state.update { withMatches(it.copy(artStatuses = it.artStatuses.toggle(status))) }

    fun clearFilters() = _state.update {
        withMatches(it.copy(filter = CardFilter(), artStatuses = emptySet(), deckCardIds = null, deckMessage = null))
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
     * Re-reads which cards have modified art. Called whenever the page is shown, since art is
     * changed on the card page while this one waits underneath.
     */
    fun refreshModified() {
        viewModelScope.launch {
            val modified = art.appliedVariants().keys
            _state.update { withMatches(it.copy(modifiedCardIds = modified)) }
        }
    }

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
                catalog = loaded
                _state.update {
                    withMatches(
                        it.copy(
                            isLoading = false,
                            totalCards = loaded.cards.size,
                            sets = loaded.sets.sortedByProductLine(),
                            traits = loaded.allTraits(),
                            modifiedCardIds = modified,
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

    /** Recomputes [CardBrowserState.cards] from the catalog, the deck list, the card filter and the art filter. */
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
        }
        return state.copy(cards = matching)
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
}
