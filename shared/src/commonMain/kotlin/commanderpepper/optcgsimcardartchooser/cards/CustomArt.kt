package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.ui.graphics.ImageBitmap
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind

/**
 * The user's own images for a card, each treated as one more variant so applying, reapplying
 * and the "Modified" filter work the same as for catalog art. A card can have several:
 * `<cardId>_custom`, `<cardId>_custom2`, `<cardId>_custom3`, …
 */
object CustomArt {
    const val LABEL = "Custom image"

    /** File types accepted from the user; converted to the sim's JPG spec either way. */
    val ACCEPTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    private val ID = Regex("""^(.+)_custom(\d*)$""")

    fun isCustom(variant: Variant): Boolean = isCustomId(variant.id)

    fun isCustomId(variantId: String): Boolean = ID.matches(variantId)

    /** 1 for `<cardId>_custom`, N for `<cardId>_customN`. */
    fun number(variantId: String): Int = ID.find(variantId)?.groupValues?.get(2)?.toIntOrNull() ?: 1

    fun variantId(cardId: String, number: Int = 1): String = if (number <= 1) "${cardId}_custom" else "${cardId}_custom$number"

    /** "Custom image", "Custom image 2", … */
    fun label(variantId: String): String = number(variantId).let { if (it <= 1) LABEL else "$LABEL $it" }

    fun variant(variantId: String): Variant = Variant(variantId, VariantKind.Other, listOf(label(variantId)))

    /** A catalog variant of [card], or one of its custom images, by ID; null if neither matches. */
    fun resolve(card: Card, variantId: String): Variant? =
        card.variants.firstOrNull { it.id == variantId }
            ?: variantId.takeIf { ID.find(it)?.groupValues?.get(1) == card.id }?.let(::variant)

    /** An image dropped from a browser: a web address or an inline `data:` image, not a local file. */
    fun isRemote(source: String): Boolean =
        source.startsWith("https://", ignoreCase = true) || source.startsWith("http://", ignoreCase = true) ||
            source.startsWith("data:", ignoreCase = true)

    /** Short name for messages: the file name, or the last part of a web address. */
    fun displayName(source: String): String = when {
        source.startsWith("data:", ignoreCase = true) -> "the dropped image"
        isRemote(source) -> source.substringBefore('?').trimEnd('/').substringAfterLast('/').ifBlank { source }
        else -> source.substringAfterLast('/').substringAfterLast('\\')
    }

    /** Whether [path] has an accepted image extension. */
    fun isAcceptedFile(path: String): Boolean =
        path.substringAfterLast('/').substringAfterLast('\\').substringAfterLast('.', "").lowercase() in ACCEPTED_EXTENSIONS
}

/** Opens a file chooser for JPG, PNG or WebP images. Returns the chosen files' paths; empty if cancelled. */
fun interface ImagePicker {
    fun pick(): List<String>
}

/** Size of the image the user picked, before conversion. */
data class CustomImageInfo(val width: Int, val height: Int) {
    /** Smaller than the sim's card art (480x671), so it gets upscaled and may look soft. */
    val isLowResolution: Boolean get() = width < 480 || height < 671
}

/** A custom image as stored by the app. */
class CustomImage(val variant: Variant, val preview: ImageBitmap?)
