package commanderpepper.optcgsimcardartchooser.catalog

import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardSet

/**
 * Hand-verified fixes for source data that is missing or wrong. Corrections always win over
 * source data, so every source should run its cards and sets through this.
 */
class CatalogCorrections(
    private val traits: Map<String, List<String>> = TRAIT_CORRECTIONS,
    private val setNames: Map<String, String> = SET_NAME_CORRECTIONS,
) {
    fun apply(card: Card): Card =
        traits[card.id]?.let { card.copy(traits = it) } ?: card

    fun apply(set: CardSet): CardSet =
        setNames[set.id]?.let { set.copy(name = it) } ?: set

    companion object {
        /** Cards whose optcgapi `sub_types` is empty, `NULL`, or another column's value. */
        val TRAIT_CORRECTIONS: Map<String, List<String>> = mapOf(
            "OP03-036" to listOf("East Blue", "Black Cat Pirates"),
            "OP03-038" to listOf("East Blue", "Krieg Pirates"),
            "OP05-040" to listOf("Donquixote Pirates"),
            "OP05-096" to listOf("Celestial Dragons"),
            "OP12-016" to listOf("Former Roger Pirates"),
            "OP12-017" to listOf("Former Roger Pirates"),
            "OP12-018" to listOf("Former Roger Pirates"),
            "OP12-019" to listOf("Former Roger Pirates"),
            "OP13-079" to listOf("?"),
            "OP17-099" to listOf("The Four Emperors", "Big Mom Pirates"),
            "P-084" to listOf("The Four Emperors", "Cross Guild"),
        )

        /** optcgapi lists EB04 inside a combined "OP14-EB04" set and has no name for ST29. */
        val SET_NAME_CORRECTIONS: Map<String, String> = mapOf(
            "EB04" to "Extra Booster: Egghead Crisis",
            "ST29" to "Starter Deck 29: Egghead",
        )
    }
}
