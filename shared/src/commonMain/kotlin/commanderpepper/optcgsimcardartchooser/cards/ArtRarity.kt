package commanderpepper.optcgsimcardartchooser.cards

import commanderpepper.optcgsimcardartchooser.catalog.conversion.ConversionScope
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind

/**
 * How rare a printing is, in the order players rank them: Manga > SP > Alt Art > Regular.
 * Bulk changes never move a card down this order.
 */
enum class ArtTier(val label: String) {
    Regular("regular art"),
    AltArt("alt art"),
    SP("SP"),
    Manga("manga art"),
    ;

    companion object {
        /** The tier of a catalog variant. Foils, promos and other special printings rank with alt arts. */
        fun of(variant: Variant): ArtTier = when (variant.kind) {
            VariantKind.Regular, VariantKind.Reprint -> Regular
            VariantKind.Manga -> Manga
            VariantKind.SP -> SP
            VariantKind.AltArt, VariantKind.Foil, VariantKind.Promo, VariantKind.Other -> AltArt
        }
    }
}

/** The rarest art [card] has — Manga, else SP, else Alt Art — or null if it has none of them. */
fun maxRarityTarget(card: Card): Variant? =
    listOf(ArtTier.Manga, ArtTier.SP, ArtTier.AltArt).firstNotNullOfOrNull { bulkTarget(card, it) }

/**
 * The art a bulk change to [tier] applies to [card]: its first art of that tier (for alt arts, a
 * true Alternate art before other special printings), with the usual exclusions; null if none.
 */
fun bulkTarget(card: Card, tier: ArtTier): Variant? {
    val candidates = card.variants.filter { ConversionScope.includes(it) && !CustomArt.isCustom(it) && ArtTier.of(it) == tier }
    return if (tier == ArtTier.AltArt) candidates.firstOrNull { it.kind == VariantKind.AltArt } ?: candidates.firstOrNull() else candidates.firstOrNull()
}
