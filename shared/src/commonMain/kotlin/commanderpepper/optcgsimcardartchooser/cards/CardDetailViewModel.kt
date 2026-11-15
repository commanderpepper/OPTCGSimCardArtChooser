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
    /** Custom images being converted after a pick or drop. */
    val isAddingImages: Boolean = false,
) {
    val hasCustomImages: Boolean get() = options.any { it.isCustom }

    /** Nothing is being applied or added, so new actions can start. */
    val isIdle: Boolean get() = applyingId == null && !isAddingImages

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
        if (path == null || !current.isIdle || optionId == current.appliedId) return
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
     * Adds picked or dropped images as new custom images. A single image is applied right away
     * (picking one image means wanting to use it); several are just added to choose from.
     * Files that aren't JPG, PNG or WebP, or can't be read, are skipped and listed in the message.
     */
    fun addCustomImages(paths: List<String>) {
        val card = _state.value.card ?: return
        if (paths.isEmpty() || !_state.value.isIdle) return
        _state.update { it.copy(isAddingImages = true, message = null) }
        viewModelScope.launch {
            val added = mutableListOf<Pair<CustomImage, CustomImageInfo>>()
            val problems = mutableListOf<String>()
            try {
                for (path in paths) {
                    val name = CustomArt.displayName(path)
                    // Browser drops arrive as web addresses, often without a file extension; the download is checked instead.
                    if (!CustomArt.isRemote(path) && !CustomArt.isAcceptedFile(path)) {
                        problems += "$name (not a JPG, PNG or WebP)"
                        continue
                    }
                    try {
                        val result = art.addCustomImage(card, path)
                        added += result
                        _state.update { s -> s.copy(options = s.options + customOption(result.first)) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        problems += "$name (${e.message ?: "unknown error"})"
                    }
                }

                val single = added.singleOrNull()
                val simCards = cardsPath
                val outcome = when {
                    added.isEmpty() -> null
                    single != null && simCards != null -> {
                        art.apply(simCards, card, single.first.variant)
                        thumbnails.invalidate(card.id)
                        _state.update { it.copy(appliedId = single.first.variant.id) }
                        "Your image is now used in the sim."
                    }
                    single != null -> "Saved your image. Verify the OPTCGSim folder to apply it."
                    else -> "Added ${added.size} images. Click one to use it."
                }
                val lowRes = added.filter { it.second.isLowResolution }
                val notes = buildList {
                    outcome?.let(::add)
                    if (lowRes.size == 1 && single != null) {
                        val info = lowRes.single().second
                        add("It's ${info.width}×${info.height}, smaller than the sim's 480×671, so it may look soft.")
                    } else if (lowRes.isNotEmpty()) {
                        add("${lowRes.size} of them are smaller than the sim's 480×671 and may look soft.")
                    }
                    if (problems.isNotEmpty()) add("Skipped ${problems.joinToString("; ")}.")
                }
                _state.update { it.copy(message = notes.joinToString(" ").ifBlank { null }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't use that image: ${e.message ?: "unknown error"}") }
            } finally {
                _state.update { it.copy(isAddingImages = false) }
            }
        }
    }

    /** Something was dropped on the page that couldn't be read as images. */
    fun reportUnreadableDrop() = _state.update {
        it.copy(
            message = "Couldn't read what was dropped. Images inside zip files, emails or some apps can't be dropped " +
                "directly; drag the image files from File Explorer, or use Add another image.",
        )
    }

    /** Swaps the picture of custom image [optionId]; if it's in use, the sim is updated too. */
    fun replaceCustomImage(optionId: String, path: String?) {
        val current = _state.value
        val card = current.card ?: return
        if (path == null || !current.isIdle || current.options.none { it.id == optionId && it.isCustom }) return
        _state.update { it.copy(applyingId = optionId, message = null) }
        viewModelScope.launch {
            try {
                val (image, info) = art.replaceCustomImage(card, optionId, path)
                _state.update { s -> s.copy(options = s.options.map { if (it.id == optionId) customOption(image) else it }) }
                val simCards = cardsPath
                if (current.appliedId == optionId && simCards != null) {
                    art.apply(simCards, card, image.variant)
                    thumbnails.invalidate(card.id)
                }
                val note = if (info.isLowResolution) " It's ${info.width}×${info.height}, smaller than the sim's 480×671, so it may look soft." else ""
                _state.update { it.copy(message = "Replaced ${image.variant.let { v -> CustomArt.label(v.id) }}.$note") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't use that image: ${e.message ?: "unknown error"}") }
            } finally {
                _state.update { it.copy(applyingId = null) }
            }
        }
    }

    /** Deletes custom image [optionId], putting the sim original back first if it's in use. */
    fun removeCustomImage(optionId: String) {
        val current = _state.value
        val card = current.card ?: return
        if (!current.isIdle || current.options.none { it.id == optionId && it.isCustom }) return
        viewModelScope.launch {
            try {
                val simCards = cardsPath
                if (current.appliedId == optionId && simCards != null) {
                    art.restoreOriginal(simCards, card)
                    thumbnails.invalidate(card.id)
                    _state.update { it.copy(appliedId = CardDetailState.ORIGINAL_ID) }
                }
                art.removeCustomImage(card, optionId)
                _state.update { s -> s.copy(options = s.options.filterNot { it.id == optionId }, message = "Removed ${CustomArt.label(optionId)}.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't remove your image: ${e.message ?: "unknown error"}") }
            }
        }
    }

    private fun customOption(image: CustomImage) =
        ArtOption(image.variant.id, CustomArt.label(image.variant.id), "Your own image", image.variant, image.preview.toArtImage())

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
        val custom = art.customImages(card).map(::customOption)
        val options = listOf(original) + variants + custom
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
