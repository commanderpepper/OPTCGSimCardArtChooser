package commanderpepper.optcgsimcardartchooser.catalog

import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardColor
import commanderpepper.optcgsimcardartchooser.catalog.model.Category
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind
import kotlin.test.Test
import kotlin.test.assertEquals

class CardFilterTest {
    private fun card(id: String, name: String, colors: List<CardColor>, category: Category, traits: List<String>, variants: Int) = Card(
        id = id, name = name, setId = id.substringBefore('-'), category = category, colors = colors, traits = traits,
        variants = List(variants) { Variant(if (it == 0) id else "${id}_p$it", if (it == 0) VariantKind.Regular else VariantKind.AltArt) },
    )

    private val cards = listOf(
        card("OP01-001", "Roronoa Zoro", listOf(CardColor.Red), Category.Leader, listOf("Supernovas", "Straw Hat Crew"), 2),
        card("OP01-061", "Kaido", listOf(CardColor.Blue, CardColor.Purple), Category.Leader, listOf("The Four Emperors", "Animal Kingdom Pirates"), 1),
        card("EB01-006", "Tony Tony.Chopper", listOf(CardColor.Red), Category.Character, listOf("Animal", "Straw Hat Crew"), 3),
    )

    private fun ids(filter: CardFilter) = cards.filteredBy(filter).map { it.id }

    @Test
    fun emptyFilterMatchesEverything() = assertEquals(3, cards.filteredBy(CardFilter()).size)

    @Test
    fun queryMatchesNameOrIdIgnoringCase() {
        assertEquals(listOf("OP01-061"), ids(CardFilter(query = "kai")))
        assertEquals(listOf("EB01-006"), ids(CardFilter(query = "eb01")))
    }

    @Test
    fun queryAlsoMatchesTraits() {
        assertEquals(listOf("OP01-001"), ids(CardFilter(query = "supernova")))
        assertEquals(listOf("OP01-001", "EB01-006"), ids(CardFilter(query = "Straw Hat")))
        // Substring search: "animal" finds both the Animal trait and Animal Kingdom Pirates.
        assertEquals(listOf("OP01-061", "EB01-006"), ids(CardFilter(query = "animal")))
    }

    @Test
    fun setFieldsMatchAnyValueAndCombineWithAnd() {
        assertEquals(listOf("OP01-001", "EB01-006"), ids(CardFilter(colors = setOf(CardColor.Red))))
        assertEquals(listOf("OP01-001"), ids(CardFilter(colors = setOf(CardColor.Red), categories = setOf(Category.Leader))))
        assertEquals(listOf("OP01-061"), ids(CardFilter(colors = setOf(CardColor.Purple))))
        assertEquals(listOf("EB01-006"), ids(CardFilter(setIds = setOf("EB01"))))
    }

    @Test
    fun traitsMatchWholeTraitsIgnoringCase() {
        assertEquals(listOf("OP01-001", "EB01-006"), ids(CardFilter(traits = setOf("straw hat crew"))))
        assertEquals(listOf("OP01-061", "EB01-006"), ids(CardFilter(traits = setOf("Animal", "The Four Emperors"))))
        // Whole-trait match: "Animal" must not match "Animal Kingdom Pirates".
        assertEquals(listOf("EB01-006"), ids(CardFilter(traits = setOf("Animal"))))
    }

    @Test
    fun alternateArtOnly() = assertEquals(listOf("OP01-001", "EB01-006"), ids(CardFilter(onlyWithAlternateArt = true)))

    @Test
    fun printingsTheAppDoesNotOfferAreNotAlternateArt() {
        val reprintOnly = Card(
            "ST01-012", "Luffy", "ST01", Category.Character, emptyList(),
            variants = listOf(
                Variant("ST01-012", VariantKind.Regular),
                Variant("ST01-012_r1", VariantKind.Reprint),
                Variant("ST01-012_p1", VariantKind.Foil, listOf("Jolly Roger Foil")),
            ),
        )
        assertEquals(emptyList(), listOf(reprintOnly).filteredBy(CardFilter(onlyWithAlternateArt = true)))
        assertEquals(0, reprintOnly.alternateArtCount)
        assertEquals(2, cards.last().alternateArtCount)
    }
}
