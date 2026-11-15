package commanderpepper.optcgsimcardartchooser.cards

import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind

/**
 * A user's own image for a card, treated as one more variant (`<cardId>_custom`) so applying,
 * reapplying and the "Modified" filter work the same as for catalog art. One per card.
 */
object CustomArt {
    const val LABEL = "Custom image"

    /** File types accepted from the user; converted to the sim's JPG spec either way. */
    val ACCEPTED_EXTENSIONS = setOf("jpg", "jpeg", "png")

    fun variantId(cardId: String): String = "${cardId}_custom"

    fun variantFor(card: Card): Variant = Variant(variantId(card.id), VariantKind.Other, listOf(LABEL))

    fun isCustom(variant: Variant): Boolean = variant.id.endsWith("_custom")

    /** A catalog variant of [card], or its custom image, by ID; null if neither matches. */
    fun resolve(card: Card, variantId: String): Variant? =
        card.variants.firstOrNull { it.id == variantId } ?: variantFor(card).takeIf { it.id == variantId }
}

/** Opens a file chooser for a JPG or PNG image. Returns the chosen file's path, or null if cancelled. */
fun interface ImagePicker {
    fun pick(): String?
}

/** Size of the image the user picked, before conversion. */
data class CustomImageInfo(val width: Int, val height: Int) {
    /** Smaller than the sim's card art (480x671), so it gets upscaled and may look soft. */
    val isLowResolution: Boolean get() = width < 480 || height < 671
}
