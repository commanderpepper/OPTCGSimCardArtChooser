package commanderpepper.optcgsimcardartchooser.catalog.optcgapi

import commanderpepper.optcgsimcardartchooser.catalog.CatalogJson
import commanderpepper.optcgsimcardartchooser.catalog.Fixtures
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardColor
import commanderpepper.optcgsimcardartchooser.catalog.model.Category
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptcgApiCatalogBuilderTest {
    private val result = OptcgApiCatalogBuilder().build(Fixtures.sets, Fixtures.decks, Fixtures.rows, "2026-11-12T00:00:00Z")
    private val catalog = result.catalog

    private fun card(id: String): Card = catalog.cards.single { it.id == id }
    private fun Card.variantIds() = variants.map { it.id }
    private fun Card.kinds() = variants.associate { it.id to it.kind }

    @Test
    fun printingsAreGroupedIntoCardsWithVariants() {
        val chopper = card("EB01-006")
        assertEquals("Tony Tony.Chopper", chopper.name)
        assertEquals(Category.Character, chopper.category)
        assertEquals(listOf(CardColor.Red), chopper.colors)
        assertEquals(3, chopper.cost)
        assertEquals(1000, chopper.counter)
        assertEquals(listOf("Animal", "Straw Hat Crew"), chopper.traits)
        assertEquals(
            mapOf("EB01-006" to VariantKind.Regular, "EB01-006_p1" to VariantKind.AltArt, "EB01-006_p2" to VariantKind.Manga),
            chopper.kinds(),
        )
        assertEquals(listOf("Alternate Art", "Manga"), chopper.variants[2].tags)
        assertEquals("https://optcgapi.com/media/static/Card_Images/EB01-006_p1.jpg", chopper.variants[1].images.single().url)
    }

    @Test
    fun numberingLabelsAreDroppedFromNamesAndTags() {
        val zoro = card("OP01-001")
        assertEquals("Roronoa Zoro", zoro.name)
        assertEquals(Category.Leader, zoro.category)
        assertEquals(emptyList(), zoro.variants.first().tags)
        assertEquals(listOf("Parallel"), zoro.variants[1].tags)
    }

    @Test
    fun duplicatePrintingPrefersTheCardsOwnSet() {
        val luffy = card("OP03-070")
        assertEquals(listOf("OP03-070"), luffy.variantIds())
        assertEquals("OP03", luffy.variants.single().sourceSetId)
        assertEquals("Monkey.D.Luffy", luffy.name)
    }

    @Test
    fun malformedVariantIdsAreNormalized() {
        assertEquals(listOf("OP09-078_r1"), card("OP09-078").variantIds())
        assertEquals(VariantKind.Reprint, card("OP09-078").variants.single().kind)
        assertEquals("PRB02", card("OP09-078").variants.single().sourceSetId)
        assertEquals(listOf("OP04-016_p1"), card("OP04-016").variantIds())
    }

    @Test
    fun crossSetPrintingKeepsItsSourceSetAndFallsBackToSuffixForKind() {
        val perona = card("OP09-034")
        assertEquals("Perona", perona.name)
        assertEquals(listOf("OP09-034", "OP09-034_p1", "EB03_OP09-034_p1"), perona.variantIds())
        val crossSet = perona.variants.last()
        assertEquals("EB03", crossSet.sourceSetId)
        assertEquals(VariantKind.AltArt, crossSet.kind)
    }

    @Test
    fun promoWithoutBasePrintingStillBecomesACard() {
        val bartolomeo = card("P-029")
        assertEquals("P", bartolomeo.setId)
        assertEquals("Bartolomeo", bartolomeo.name)
        assertEquals(
            mapOf("P-029_p3" to VariantKind.Foil, "P-029_p4" to VariantKind.AltArt, "P-029_r2" to VariantKind.Reprint),
            bartolomeo.kinds(),
        )
    }

    @Test
    fun multiColorAndMissingAttributesAreParsed() {
        assertEquals(listOf(CardColor.Blue, CardColor.Purple), card("OP01-061").colors)
        assertEquals(emptyList(), card("OP03-098").attributes)
        assertEquals(listOf("Strike"), card("EB01-006").attributes)
    }

    @Test
    fun setsUseSimStyleIdsAndNamesFromSetsAndDecks() {
        val names = catalog.sets.associate { it.id to it.name }
        assertEquals("Romance Dawn", names["OP01"])
        assertEquals("Starter Deck 1: Straw Hat Crew", names["ST01"])
        assertEquals("Promotion", names["P"])
        assertEquals(catalog.cards.map { it.setId }.distinct().sorted(), catalog.sets.map { it.id })
    }

    @Test
    fun cardIdWithVariantSuffixIsGroupedUnderTheBaseCard() {
        val rows = listOf(
            ApiCardRow(cardSetId = "P-029_r1", cardImageId = "P-029_r1", cardName = "Bartolomeo (Reprint)", cardType = "Character", setId = "PRB-01"),
            ApiCardRow(cardSetId = null, cardImageId = null, cardName = "junk", cardType = "Character"),
            ApiCardRow(cardSetId = "OP01-002", cardImageId = "OP01-002", cardName = "Mystery", cardType = "Don"),
        )
        val built = OptcgApiCatalogBuilder().build(emptyList(), emptyList(), rows, "t")
        assertEquals(listOf("P-029"), built.catalog.cards.map { it.id })
        assertEquals(2, built.warnings.size, built.warnings.toString())
    }

    @Test
    fun junkBaseTraitsFallBackToOtherPrintingsButOtherFieldsDoNot() {
        // Real OP08-001 data: the base printing's sub_types is "5000", the alt art's is correct.
        // Reprint rows carry placeholders (counter 0, disclaimer text) that must not leak in.
        val rows = listOf(
            ApiCardRow(cardSetId = "OP08-001", cardImageId = "OP08-001", cardName = "Tony Tony.Chopper", cardType = "Leader", subTypes = "5000", setId = "OP-08"),
            ApiCardRow(
                cardSetId = "OP08-001", cardImageId = "OP08-001_p1", cardName = "Tony Tony.Chopper (Parallel)", cardType = "Leader",
                subTypes = "Animal Straw Hat Crew Drum Kingdom", cardText = "DISCLAIMER: reprint", counterAmount = JsonPrimitive(0), setId = "OP-08",
            ),
        )
        val chopper = OptcgApiCatalogBuilder().build(emptyList(), emptyList(), rows, "t").catalog.cards.single()
        assertEquals(listOf("Animal", "Straw Hat Crew", "Drum Kingdom"), chopper.traits)
        assertEquals("", chopper.text)
        assertEquals(null, chopper.counter)
        assertEquals("Tony Tony.Chopper", chopper.name)
    }

    @Test
    fun handVerifiedCorrectionsOverrideSourceTraits() {
        val rows = listOf(
            ApiCardRow(cardSetId = "OP17-099", cardImageId = "OP17-099", cardName = "Charlotte Linlin", cardType = "Leader", subTypes = "Special", setId = "OP-17"),
            ApiCardRow(cardSetId = "OP05-096", cardImageId = "OP05-096", cardName = "I Bid 500 Million!!", cardType = "Event", subTypes = "", setId = "OP-05"),
        )
        val cards = OptcgApiCatalogBuilder().build(emptyList(), emptyList(), rows, "t").catalog.cards.associateBy { it.id }
        assertEquals(listOf("The Four Emperors", "Big Mom Pirates"), cards.getValue("OP17-099").traits)
        assertEquals(listOf("Celestial Dragons"), cards.getValue("OP05-096").traits)
    }

    @Test
    fun handVerifiedCorrectionsFixMisspelledNames() {
        val rows = listOf(
            ApiCardRow(cardSetId = "OP16-065", cardImageId = "OP16-065", cardName = "Sakazuk", cardType = "Character", setId = "OP-16"),
            ApiCardRow(cardSetId = "OP16-065", cardImageId = "OP16-065_p1", cardName = "Sakazuk (Parallel)", cardType = "Character", setId = "OP-16"),
        )
        val card = OptcgApiCatalogBuilder().build(emptyList(), emptyList(), rows, "t").catalog.cards.single()
        assertEquals("Sakazuki", card.name)
        assertEquals(listOf(listOf(), listOf("Parallel")), card.variants.map { it.tags })
    }

    @Test
    fun setNameCorrectionsFixCombinedAndUnnamedSets() {
        // The real API: EB04 only exists inside "OP14-EB04", and ST29 has no deck entry.
        val sets = listOf(ApiSet(setId = "OP14-EB04", setName = "The Azure Sea's Seven"))
        val rows = listOf("OP14-001", "EB04-001", "ST29-001").map {
            ApiCardRow(cardSetId = it, cardImageId = it, cardName = "Card", cardType = "Character", setId = "OP14-EB04")
        }
        val names = OptcgApiCatalogBuilder().build(sets, emptyList(), rows, "t").catalog.sets.associate { it.id to it.name }
        assertEquals(
            mapOf("EB04" to "Extra Booster: Egghead Crisis", "OP14" to "The Azure Sea's Seven", "ST29" to "Starter Deck 29: Egghead"),
            names,
        )
    }

    @Test
    fun catalogSurvivesJsonRoundTrip() {
        assertEquals(catalog, CatalogJson.decode(CatalogJson.encode(catalog)))
        assertTrue(result.warnings.isEmpty(), result.warnings.toString())
    }

    @Test
    fun helperNormalization() {
        assertEquals(listOf("OP14", "EB04"), OptcgApiCatalogBuilder.setTokens("OP14-EB04"))
        assertEquals(listOf("OP01"), OptcgApiCatalogBuilder.setTokens("OP-01"))
        assertEquals("OP09-078_r1", OptcgApiCatalogBuilder.normalizeVariantId("OP09-078-r1"))
        assertEquals("OP01-001", OptcgApiCatalogBuilder.normalizeVariantId("OP01-001"))
        assertEquals(listOf("Jolly Roger Foil"), OptcgApiCatalogBuilder.nameTags("Bartolomeo (P-029) (Jolly Roger Foil)"))
    }
}
