package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.ui.graphics.ImageBitmap
import commanderpepper.optcgsimcardartchooser.catalog.model.Card

/** Loads small card images for lists and grids. */
interface CardThumbnails {
    /**
     * The thumbnail for [card]'s current art, given the sim's `StreamingAssets/Cards` folder,
     * or null when no image exists or it can't be decoded.
     */
    suspend fun load(cardsPath: String, card: Card): ImageBitmap?
}
