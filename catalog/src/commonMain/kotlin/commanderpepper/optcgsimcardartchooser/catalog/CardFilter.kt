package commanderpepper.optcgsimcardartchooser.catalog

import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardColor
import commanderpepper.optcgsimcardartchooser.catalog.model.Category

/** Empty fields match everything; set fields match if the card has any of the values. */
data class CardFilter(
    /** Free text matched (case-insensitively, as a substring) against name, card number and traits. */
    val query: String = "",
    val setIds: Set<String> = emptySet(),
    val colors: Set<CardColor> = emptySet(),
    val categories: Set<Category> = emptySet(),
    val traits: Set<String> = emptySet(),
    val onlyWithAlternateArt: Boolean = false,
) {
    fun matches(card: Card): Boolean =
        (query.isBlank() || matchesQuery(card, query.trim())) &&
            (setIds.isEmpty() || card.setId in setIds) &&
            (colors.isEmpty() || card.colors.any { it in colors }) &&
            (categories.isEmpty() || card.category in categories) &&
            (traits.isEmpty() || card.traits.any { trait -> traits.any { it.equals(trait, ignoreCase = true) } }) &&
            (!onlyWithAlternateArt || card.variants.size > 1)

    private fun matchesQuery(card: Card, text: String): Boolean =
        card.name.contains(text, ignoreCase = true) ||
            card.id.contains(text, ignoreCase = true) ||
            card.traits.any { it.contains(text, ignoreCase = true) }
}

fun List<Card>.filteredBy(filter: CardFilter): List<Card> = filter(filter::matches)
