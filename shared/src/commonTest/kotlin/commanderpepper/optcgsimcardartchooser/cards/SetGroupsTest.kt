package commanderpepper.optcgsimcardartchooser.cards

import commanderpepper.optcgsimcardartchooser.catalog.model.CardSet
import kotlin.test.Test
import kotlin.test.assertEquals

class SetGroupsTest {
    // Shuffled the way the catalog's alphabetical order would put them (EB < OP < P < PRB < ST, ST10 < ST2).
    private val sets = listOf("ST10", "P", "EB02", "OP10", "PRB01", "ST2", "OP02", "EB01", "OP01", "ST01", "XY01")
        .map { CardSet(it, "name $it") }

    @Test
    fun ordersByProductLineThenNumber() {
        assertEquals(
            listOf("OP01", "OP02", "OP10", "EB01", "EB02", "ST01", "ST2", "ST10", "PRB01", "P", "XY01"),
            sets.sortedByProductLine().map { it.id },
        )
    }

    @Test
    fun groupsKeepThatOrderAndSkipEmptyGroups() {
        val groups = sets.groupedByProductLine()
        assertEquals(
            listOf(SetGroup.Booster, SetGroup.ExtraBooster, SetGroup.StarterDeck, SetGroup.PremiumBooster, SetGroup.Promotion, SetGroup.Other),
            groups.map { it.first },
        )
        assertEquals(listOf("ST01", "ST2", "ST10"), groups.first { it.first == SetGroup.StarterDeck }.second.map { it.id })
        assertEquals(emptyList(), listOf(CardSet("OP01", "x")).groupedByProductLine().filter { it.first != SetGroup.Booster })
    }

    @Test
    fun prbIsNotMistakenForPromotions() {
        assertEquals(SetGroup.PremiumBooster, SetGroup.of("PRB02"))
        assertEquals(SetGroup.Promotion, SetGroup.of("P"))
    }
}
