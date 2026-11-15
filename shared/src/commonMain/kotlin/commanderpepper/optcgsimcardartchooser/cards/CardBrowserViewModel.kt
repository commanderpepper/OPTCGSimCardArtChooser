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
import commanderpepper.optcgsimcardartchooser.simfolder.FolderCheck
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderLocator
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderStore
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

data class CardBrowserState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val filter: CardFilter = CardFilter(),
    /** Cards matching [filter], in catalog order. */
    val cards: List<Card> = emptyList(),
    val totalCards: Int = 0,
    /** Options for the set filter, ordered by product line (OP, EB, ST, PRB, P) then number. */
    val sets: List<CardSet> = emptyList(),
    /** Options for the trait filter. */
    val traits: List<String> = emptyList(),
) {
    val hasActiveFilter: Boolean get() = filter != CardFilter()
}

@Inject
@ViewModelKey(CardBrowserViewModel::class)
@ContributesIntoMap(AppScope::class)
class CardBrowserViewModel(
    private val repository: CatalogRepository,
    private val thumbnails: CardThumbnails,
    simFolderStore: SimFolderStore,
    simFolderLocator: SimFolderLocator,
) : ViewModel() {

    private val _state = MutableStateFlow(CardBrowserState())
    val state: StateFlow<CardBrowserState> = _state.asStateFlow()

    private var catalog: Catalog? = null

    /** The verified sim folder's `StreamingAssets/Cards` directory, resolved when the page opens. */
    private val cardsPath: String? =
        (simFolderStore.load()?.let(simFolderLocator::check) as? FolderCheck.Valid)?.cardsPath

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

    fun clearFilters() = updateFilter { CardFilter() }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val loaded = repository.load()
                catalog = loaded
                _state.update {
                    it.copy(
                        isLoading = false,
                        totalCards = loaded.cards.size,
                        sets = loaded.sets.sortedByProductLine(),
                        traits = loaded.allTraits(),
                        cards = loaded.cards.filteredBy(it.filter),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load the card catalog") }
            }
        }
    }

    private fun updateFilter(change: (CardFilter) -> CardFilter) {
        _state.update { current ->
            val filter = change(current.filter)
            current.copy(filter = filter, cards = catalog?.cards?.filteredBy(filter).orEmpty())
        }
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
}
