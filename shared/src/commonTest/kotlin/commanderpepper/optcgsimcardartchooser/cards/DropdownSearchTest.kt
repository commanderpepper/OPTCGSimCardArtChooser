package commanderpepper.optcgsimcardartchooser.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DropdownSearchTest {
    private val traits = listOf(DropdownSection(null, listOf("Animal", "Animal Kingdom Pirates", "Donquixote Pirates", "Dressrosa", "Straw Hat Crew", "?")))

    private fun labels(search: String) = traits.narrowedTo(search) { it }.flatMap { it.options }

    @Test
    fun searchNarrowsByCaseInsensitiveSubstring() {
        assertEquals(listOf("Animal Kingdom Pirates", "Donquixote Pirates"), labels("pir"))
        assertEquals(listOf("Animal", "Animal Kingdom Pirates"), labels("ANIMAL"))
        assertEquals(listOf("Dressrosa"), labels("  rosa "))
        assertEquals(listOf("?"), labels("?"))
    }

    @Test
    fun blankSearchShowsEverythingAndNoMatchShowsNothing() {
        assertEquals(6, labels("").size)
        assertTrue(labels("zzz").isEmpty(), "empty sections are dropped so the menu can say nothing matches")
    }

    @Test
    fun sectionsKeepTheirTitles() {
        val sets = listOf(DropdownSection("Booster Packs", listOf("OP01", "OP02")), DropdownSection("Starter Decks", listOf("ST01")))
        assertEquals(listOf("Starter Decks"), sets.narrowedTo("st") { it }.map { it.title })
    }
}
