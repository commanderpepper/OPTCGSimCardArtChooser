package commanderpepper.optcgsimcardartchooser.catalog.model

import kotlinx.serialization.Serializable

/**
 * The app's own card catalog format. Independent of any data source: each source is
 * converted into this shape, so sources can be swapped or merged.
 */
@Serializable
data class Catalog(
    val schemaVersion: Int,
    /** ISO-8601 instant the catalog was built. */
    val generatedAt: String,
    val sets: List<CardSet>,
    val cards: List<Card>,
) {
    /** Every trait used by any card, sorted alphabetically. */
    fun allTraits(): List<String> =
        cards.flatMap { it.traits }.distinct().sortedBy { it.lowercase() }

    companion object {
        /** 2: `Card.traits` changed from a raw string to a list. */
        const val SCHEMA_VERSION = 2
    }
}

@Serializable
data class CardSet(
    /** Matches the sim's folder name and card ID prefix, e.g. `OP01`, `ST01`, `P`. */
    val id: String,
    val name: String,
    val block: Int? = null,
)

@Serializable
data class Card(
    /** e.g. `OP01-001`. The sim's file name and the key for selections. */
    val id: String,
    val name: String,
    val setId: String,
    val category: Category,
    val colors: List<CardColor>,
    val cost: Int? = null,
    val power: Int? = null,
    val counter: Int? = null,
    val life: Int? = null,
    val attributes: List<String> = emptyList(),
    /** e.g. `["Animal", "Straw Hat Crew"]`. */
    val traits: List<String> = emptyList(),
    val rarity: String? = null,
    val text: String = "",
    val block: Int? = null,
    val variants: List<Variant>,
)

@Serializable
data class Variant(
    /** Unique per printing, e.g. `OP01-001`, `OP01-001_p1`, `OP09-078_r1`. */
    val id: String,
    val kind: VariantKind,
    /** Labels from the source, e.g. `["Alternate Art", "Manga"]`. */
    val tags: List<String> = emptyList(),
    val language: String = "en",
    /** Set this printing appeared in; differs from the card's set for reprints and promos. */
    val sourceSetId: String? = null,
    /** Every known place to download this art, in order of preference. */
    val images: List<ImageSource> = emptyList(),
)

@Serializable
data class ImageSource(val url: String, val origin: String)

@Serializable
enum class Category { Leader, Character, Event, Stage }

@Serializable
enum class CardColor { Red, Green, Blue, Purple, Black, Yellow }

@Serializable
enum class VariantKind { Regular, AltArt, Manga, SP, Foil, Promo, Reprint, Other }
