package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.ui.graphics.ImageBitmap
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant

/** Previews card art and swaps it in the sim folder. */
interface CardArt {
    /** The variant's sim-ready art, downloading and converting it first if needed; null if unavailable. */
    suspend fun variantPreview(card: Card, variant: Variant): ImageBitmap?

    /** The sim's original art: the backup if the card was changed, otherwise its current file. */
    suspend fun originalPreview(cardsPath: String, card: Card): ImageBitmap?

    /**
     * Copies [variant]'s art into the sim as the card's image (full and small). The sim's
     * original files are backed up the first time a card is changed, and never overwritten.
     * Throws with a readable message if the art can't be prepared or copied.
     */
    suspend fun apply(cardsPath: String, card: Card, variant: Variant)

    /** Puts the sim's original art back and forgets the selection. */
    suspend fun restoreOriginal(cardsPath: String, card: Card)

    /** The variant currently applied to the sim for [cardId], or null if it shows the original. */
    suspend fun appliedVariant(cardId: String): String?

    /** Every card whose art was changed, as card ID → applied variant ID. */
    suspend fun appliedVariants(): Map<String, String>

    /**
     * Puts [variant]'s art back after something (typically a sim update) replaced it. Files the
     * sim replaced become the new backups, since they're its current originals.
     * Throws with a readable message if the art can't be prepared or copied.
     */
    suspend fun reapply(cardsPath: String, card: Card, variant: Variant): ReapplyOutcome
}

enum class ReapplyOutcome {
    /** The sim had replaced the art; it's been put back. */
    Reapplied,
    /** The sim still had the app's art; nothing was changed. */
    AlreadyApplied,
}
