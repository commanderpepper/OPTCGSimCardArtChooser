package commanderpepper.optcgsimcardartchooser.catalog

import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardColor
import commanderpepper.optcgsimcardartchooser.catalog.model.Category

/** Empty fields match everything; set fields match if the card has any of the values. */
data class CardFilter(
    val query: String = "",
    val setIds: Set<String> = emptySet(),
    val colors: Set<CardColor> = emptySet(),
    val categories: Set<Category> = emptySet(),
    val traits: Set<String> = emptySet(),
    val onlyWithAlternateArt: Boolean = false,
) {
    fun matches(card: Card): Boolean =
        (query.isBlank() || card.name.contains(query.trim(), ignoreCase = true) || card.id.contains(query.trim(), ignoreCase = true)) &&
            (setIds.isEmpty() || card.setId in setIds) &&
            (colors.isEmpty() || card.colors.any { it in colors }) &&
            (categories.isEmpty() || card.category in categories) &&
            (traits.isEmpty() || card.traits.any { trait -> traits.any { it.equals(trait, ignoreCase = true) } }) &&
            (!onlyWithAlternateArt || card.variants.size > 1)
}

fun List<Card>.filteredBy(filter: CardFilter): List<Card> = filter(filter::matches)
