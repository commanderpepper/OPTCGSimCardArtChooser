package commanderpepper.optcgsimcardartchooser.catalog.traits

import kotlin.test.Test
import kotlin.test.assertEquals

// Inputs are real optcgapi `sub_types` values.
class TraitSplitterTest {
    private val splitter = TraitSplitter()
    private fun traits(raw: String) = splitter.split(raw).traits

    @Test
    fun splitsSpaceJoinedMultiWordTraits() {
        assertEquals(listOf("Animal", "Straw Hat Crew"), traits("Animal Straw Hat Crew"))
        assertEquals(listOf("Donquixote Pirates", "The Seven Warlords of the Sea"), traits("Donquixote Pirates The Seven Warlords of the Sea"))
        assertEquals(
            listOf("Fish-Man", "The Seven Warlords of the Sea", "The Sun Pirates"),
            traits("Fish-Man The Seven Warlords of the Sea The Sun Pirates"),
        )
        assertEquals(
            listOf("Former Whitebeard Pirates", "Land of Wano", "Minks", "The Akazaya Nine"),
            traits("Former Whitebeard Pirates Land of Wano Minks The Akazaya Nine"),
        )
    }

    @Test
    fun prefersTheFewestLongestTraits() {
        assertEquals(listOf("Animal Kingdom Pirates", "The Four Emperors"), traits("Animal Kingdom Pirates The Four Emperors"))
        assertEquals(listOf("Giant", "Whitebeard Pirates Allies"), traits("Giant Whitebeard Pirates Allies"))
        assertEquals(listOf("Fish-Man", "Former Arlong Pirates", "Fish-Man Island"), traits("Fish-Man Former Arlong Pirates Fish-Man Island"))
        assertEquals(listOf("Roger Pirates", "King of the Pirates"), traits("Roger Pirates King of the Pirates"))
    }

    @Test
    fun matchingIgnoresCaseAndReturnsCanonicalSpelling() {
        assertEquals(listOf("FILM", "Neo Navy"), traits("Film Neo Navy"))
        assertEquals(listOf("Animal Kingdom Pirates", "SMILE"), traits("Animal Kingdom Pirates Smile"))
    }

    @Test
    fun aliasesAndSeparatorsAreHandled() {
        assertEquals(listOf("Straw Hat Crew"), traits("Straw Hat Cre"))
        assertEquals(listOf("Giant", "New Giant Pirates", "Dressrosa"), traits("Giant New Giant Pirate Crew Dressrosa"))
        assertEquals(listOf("Animal", "Straw Hat Crew"), traits("Animal/Straw Hat Crew"))
    }

    @Test
    fun junkValuesProduceNoTraits() {
        listOf("NULL", "5000", "Special", "", "   ").forEach { assertEquals(emptyList(), traits(it), it) }
        assertEquals(emptyList(), splitter.split(null).traits)
    }

    @Test
    fun questionMarkIsARealTrait() {
        assertEquals(listOf("?"), traits("?"))
        assertEquals(emptyList(), splitter.split("?").unknown)
    }

    @Test
    fun unknownWordRunsAreKeptTogetherAndReported() {
        val result = splitter.split("Straw Hat Crew Brand New Crew Supernovas")
        assertEquals(listOf("Straw Hat Crew", "Brand New Crew", "Supernovas"), result.traits)
        assertEquals(listOf("Brand New Crew"), result.unknown)
    }

    @Test
    fun duplicatesAreRemoved() {
        assertEquals(listOf("Mountain Bandits"), traits("Mountain Bandits Mountain Bandits"))
    }
}
