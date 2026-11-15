package commanderpepper.optcgsimcardartchooser.cards

import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.Category
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomArtTest {
    private val card = Card("OP01-001", "Zoro", "OP01", Category.Leader, emptyList(), variants = listOf(Variant("OP01-001_p1", VariantKind.AltArt)))

    @Test
    fun idsNumberAndLabelsForSeveralImages() {
        assertEquals("OP01-001_custom", CustomArt.variantId("OP01-001", 1), "the first keeps the original single-image ID")
        assertEquals("OP01-001_custom2", CustomArt.variantId("OP01-001", 2))
        assertEquals(1, CustomArt.number("OP01-001_custom"))
        assertEquals(12, CustomArt.number("OP01-001_custom12"))
        assertEquals("Custom image", CustomArt.label("OP01-001_custom"))
        assertEquals("Custom image 3", CustomArt.label("OP01-001_custom3"))
    }

    @Test
    fun resolveFindsCatalogVariantsAndThisCardsCustomImagesOnly() {
        assertEquals(VariantKind.AltArt, CustomArt.resolve(card, "OP01-001_p1")?.kind)
        assertTrue(CustomArt.resolve(card, "OP01-001_custom2")!!.let(CustomArt::isCustom))
        assertNull(CustomArt.resolve(card, "OP01-002_custom"), "another card's custom image")
        assertNull(CustomArt.resolve(card, "OP01-001_p9"))
    }

    @Test
    fun acceptedFilesAreJpgPngAndWebpInAnyCase() {
        listOf("a.jpg", "b.JPEG", "C:\\x\\c.Png", "/home/d.png", "e.webp", "F.WEBP").forEach { assertTrue(CustomArt.isAcceptedFile(it), it) }
        listOf("a.gif", "b.avif", "noextension", "C:\\png\\folder").forEach { assertFalse(CustomArt.isAcceptedFile(it), it) }
    }
}
