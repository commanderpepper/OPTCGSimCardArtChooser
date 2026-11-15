package commanderpepper.optcgsimcardartchooser.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import commanderpepper.optcgsimcardartchooser.cards.CardArt
import commanderpepper.optcgsimcardartchooser.cards.CatalogRepository
import commanderpepper.optcgsimcardartchooser.cards.CustomArt
import commanderpepper.optcgsimcardartchooser.cards.ReapplyOutcome
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

data class HomeState(
    /** Cards whose art was changed through the app. */
    val modifiedCount: Int = 0,
    /** Set while reapplying: (cards done, total). */
    val progress: Pair<Int, Int>? = null,
    val message: String? = null,
) {
    val isReapplying: Boolean get() = progress != null
}

/**
 * Home page actions. A sim update overwrites the art this app applied; [reapplyAll] puts every
 * modified card's chosen art back.
 */
@Inject
@ViewModelKey(HomeViewModel::class)
@ContributesIntoMap(AppScope::class)
class HomeViewModel(
    private val art: CardArt,
    private val catalogRepository: CatalogRepository,
    private val simCardsFolder: SimCardsFolder,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /** Re-reads how many cards are modified; called whenever Home is shown. */
    fun refresh() {
        viewModelScope.launch {
            val count = art.appliedVariants().size
            _state.update { it.copy(modifiedCount = count) }
        }
    }

    fun reapplyAll() {
        if (_state.value.isReapplying) return
        val cardsPath = simCardsFolder.cardsPath
        if (cardsPath == null) {
            _state.update { it.copy(message = "The OPTCGSim folder isn't verified, so nothing can be reapplied.") }
            return
        }
        viewModelScope.launch {
            val selections = art.appliedVariants()
            _state.update { it.copy(progress = 0 to selections.size, message = null) }
            val reapplied = mutableListOf<String>()
            val unchanged = mutableListOf<String>()
            val failed = mutableListOf<String>()
            try {
                val cards = catalogRepository.load().cards.associateBy { it.id }
                // One at a time: a card whose converted art is missing may need a download.
                selections.entries.forEachIndexed { index, (cardId, variantId) ->
                    val card = cards[cardId]
                    val variant = card?.let { CustomArt.resolve(it, variantId) }
                    if (card == null || variant == null) {
                        failed += "$cardId (no longer in the catalog)"
                    } else {
                        try {
                            when (art.reapply(cardsPath, card, variant)) {
                                ReapplyOutcome.Reapplied -> reapplied += cardId
                                ReapplyOutcome.AlreadyApplied -> unchanged += cardId
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed += "$cardId (${e.message ?: "unknown error"})"
                        }
                    }
                    _state.update { it.copy(progress = index + 1 to selections.size) }
                }
                _state.update { it.copy(message = summary(reapplied.size, unchanged.size, failed)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't reapply: ${e.message ?: "unknown error"}") }
            } finally {
                _state.update { it.copy(progress = null, modifiedCount = selections.size) }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun summary(reapplied: Int, unchanged: Int, failed: List<String>): String = buildList {
        if (reapplied == 0 && unchanged == 0 && failed.isEmpty()) add("No modified cards to reapply.")
        if (reapplied > 0) add("Reapplied $reapplied ${cards(reapplied)}.")
        if (unchanged > 0) add("$unchanged ${cards(unchanged)} already had ${if (unchanged == 1) "its" else "their"} art.")
        if (failed.isNotEmpty()) add("Couldn't reapply ${failed.size}: ${failed.joinToString("; ")}")
    }.joinToString(" ")

    private fun cards(n: Int) = if (n == 1) "card" else "cards"
}
