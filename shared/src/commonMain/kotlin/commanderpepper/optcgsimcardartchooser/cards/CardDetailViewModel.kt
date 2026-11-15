package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import commanderpepper.optcgsimcardartchooser.catalog.conversion.ConversionScope
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind
import commanderpepper.optcgsimcardartchooser.simfolder.SimCardsFolder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ArtImage {
    data object Loading : ArtImage
    data class Loaded(val bitmap: ImageBitmap) : ArtImage
    /** Couldn't be found, downloaded or converted: shown as "image not found" and can't be selected. */
    data object Unavailable : ArtImage
}

/** One choice on the card page: the sim's original art, or a catalog variant. */
data class ArtOption(
    val id: String,
    val title: String,
    val subtitle: String?,
    val variant: Variant?,
    val image: ArtImage = ArtImage.Loading,
) {
    val isOriginal: Boolean get() = variant == null
    val isCustom: Boolean get() = variant != null && CustomArt.isCustom(variant)
}

data class CardDetailState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val card: Card? = null,
    val options: List<ArtOption> = emptyList(),
    /** Option currently in the sim. */
    val appliedId: String = ORIGINAL_ID,
    /** Option being copied into the sim right now, if any. */
    val applyingId: String? = null,
    /** False when no verified sim folder exists, so nothing can be applied. */
    val canApply: Boolean = true,
    val message: String? = null,
) {
    /** The card has no custom image yet, so the "use your own image" tile is shown. */
    val canAddCustomImage: Boolean get() = card != null && options.none { it.isCustom }

    companion object {
        const val ORIGINAL_ID = "original"
    }
}

/**
 * The art options for one card. Clicking an option copies it into the sim folder right away;
 * clicking "Sim original" restores the backed-up art.
 */
@AssistedInject
class CardDetailViewModel(
    @Assisted private val cardId: String,
    private val catalogRepository: CatalogRepository,
    private val art: CardArt,
    private val thumbnails: CardThumbnails,
    simCardsFolder: SimCardsFolder,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(cardId: String): CardDetailViewModel
    }

    private val cardsPath: String? = simCardsFolder.cardsPath

    private val _state = MutableStateFlow(CardDetailState(canApply = cardsPath != null))
    val state: StateFlow<CardDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun select(optionId: String) {
        val current = _state.value
        val card = current.card ?: return
        val option = current.options.firstOrNull { it.id == optionId } ?: return
        val path = cardsPath
        if (path == null || current.applyingId != null || optionId == current.appliedId) return
        if (option.image is ArtImage.Unavailable) return

        _state.update { it.copy(applyingId = optionId, message = null) }
        viewModelScope.launch {
            try {
                val variant = option.variant
                if (variant == null) art.restoreOriginal(path, card) else art.apply(path, card, variant)
                thumbnails.invalidate(card.id)
                _state.update { it.copy(appliedId = optionId, message = "${option.title} is now used in the sim.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't apply ${option.title}: ${e.message ?: "unknown error"}") }
            } finally {
                _state.update { it.copy(applyingId = null) }
            }
        }
    }

    /**
     * Converts the image at [path] into this card's custom art (replacing any previous one) and
     * applies it, since picking an image means wanting to use it. Null [path] = picker cancelled.
     */
    fun useCustomImage(path: String?) {
        val card = _state.value.card ?: return
        if (path == null || _state.value.applyingId != null) return
        val variant = CustomArt.variantFor(card)
        _state.update { it.copy(applyingId = variant.id, message = null) }
        viewModelScope.launch {
            try {
                val info = art.setCustomImage(card, path)
                val option = ArtOption(variant.id, CustomArt.LABEL, "Your own image", variant, art.customPreview(card).toArtImage())
                _state.update { s ->
                    val others = s.options.filterNot { it.isCustom }
                    s.copy(options = others + option)
                }
                val blurryNote = if (info.isLowResolution) {
                    " It's ${info.width}×${info.height}, smaller than the sim's 480×671, so it may look soft."
                } else {
                    ""
                }
                val simCards = cardsPath
                if (simCards == null) {
                    _state.update { it.copy(message = "Saved your image. Verify the OPTCGSim folder to apply it.$blurryNote") }
                } else {
                    art.apply(simCards, card, variant)
                    thumbnails.invalidate(card.id)
                    _state.update { it.copy(appliedId = variant.id, message = "Your image is now used in the sim.$blurryNote") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't use that image: ${e.message ?: "unknown error"}") }
            } finally {
                _state.update { it.copy(applyingId = null) }
            }
        }
    }

    /** Deletes this card's custom image, putting the sim original back first if it's in use. */
    fun removeCustomImage() {
        val current = _state.value
        val card = current.card ?: return
        if (current.applyingId != null) return
        val customId = CustomArt.variantId(card.id)
        viewModelScope.launch {
            try {
                val simCards = cardsPath
                if (current.appliedId == customId && simCards != null) {
                    art.restoreOriginal(simCards, card)
                    thumbnails.invalidate(card.id)
                    _state.update { it.copy(appliedId = CardDetailState.ORIGINAL_ID) }
                }
                art.removeCustomImage(card)
                _state.update { s -> s.copy(options = s.options.filterNot { it.isCustom }, message = "Removed your image.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't remove your image: ${e.message ?: "unknown error"}") }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun ImageBitmap?.toArtImage(): ArtImage = this?.let(ArtImage::Loaded) ?: ArtImage.Unavailable

    private suspend fun load() {
        val card = try {
            catalogRepository.load().cards.firstOrNull { it.id == cardId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load the card catalog") }
            return
        }
        if (card == null) {
            _state.update { it.copy(isLoading = false, error = "Card $cardId isn't in the catalog.") }
            return
        }

        val original = ArtOption(CardDetailState.ORIGINAL_ID, "Sim original", "The art the sim shipped with", variant = null)
        val variants = card.variants.filter(ConversionScope::includes).map { variant ->
            ArtOption(variant.id, titleFor(variant), variant.tags.joinToString(" · ").ifBlank { null }, variant)
        }
        val customImage = art.customPreview(card)
        val custom = customImage?.let {
            val variant = CustomArt.variantFor(card)
            ArtOption(variant.id, CustomArt.LABEL, "Your own image", variant, ArtImage.Loaded(it))
        }
        val options = listOf(original) + variants + listOfNotNull(custom)
        val applied = art.appliedVariant(card.id)?.takeIf { id -> options.any { it.id == id } } ?: CardDetailState.ORIGINAL_ID
        _state.update { it.copy(isLoading = false, card = card, options = options, appliedId = applied) }

        // Previews one at a time: variants that aren't converted yet are downloaded, so go easy on the host.
        setImage(original.id, cardsPath?.let { art.originalPreview(it, card) })
        variants.forEach { option -> setImage(option.id, art.variantPreview(card, option.variant!!)) }
    }

    private fun setImage(optionId: String, bitmap: ImageBitmap?) {
        val image = bitmap?.let(ArtImage::Loaded) ?: ArtImage.Unavailable
        _state.update { s -> s.copy(options = s.options.map { if (it.id == optionId) it.copy(image = image) else it }) }
    }

    private fun titleFor(variant: Variant): String = when (variant.kind) {
        VariantKind.Regular -> "Regular"
        VariantKind.AltArt -> "Alternate art"
        VariantKind.Manga -> "Manga"
        VariantKind.SP -> "SP"
        VariantKind.Foil -> "Foil"
        VariantKind.Promo -> "Promo"
        VariantKind.Reprint -> "Reprint"
        VariantKind.Other -> variant.id
    }
}
