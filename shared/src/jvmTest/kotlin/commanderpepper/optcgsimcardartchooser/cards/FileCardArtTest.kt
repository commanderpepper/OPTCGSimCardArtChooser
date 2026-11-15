package commanderpepper.optcgsimcardartchooser.cards

import commanderpepper.optcgsimcardartchooser.catalog.conversion.AppDirectories
import commanderpepper.optcgsimcardartchooser.catalog.conversion.SetConverter
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.Category
import commanderpepper.optcgsimcardartchooser.catalog.model.ImageSource
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileCardArtTest {
    private val root: Path = Files.createTempDirectory("card-art-test")
    private val dirs = AppDirectories(data = root.resolve("data"), cache = root.resolve("cache"))
    private val cards = root.resolve("sim/Cards").also { it.createDirectories() }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun encoded(color: Color, width: Int, height: Int, format: String): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { this.color = color; fillRect(0, 0, width, height); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    private val http = HttpClient(MockEngine { request ->
        when {
            "missing" in request.url.toString() -> respond("nope", HttpStatusCode.NotFound)
            else -> respond(encoded(Color.GREEN, 600, 838, "png"), HttpStatusCode.OK)
        }
    })
    private val converter = SetConverter(http, dirs, requestDelayMillis = 0)
    private val art = FileCardArt(dirs, converter)

    private fun card(id: String) = Card(
        id, "name", id.substringBefore('-'), Category.Leader, emptyList(),
        variants = listOf(
            Variant("${id}_p1", VariantKind.AltArt, images = listOf(ImageSource("https://img/${id}_p1.jpg", "test"))),
            Variant("${id}_p2", VariantKind.AltArt, images = listOf(ImageSource("https://img/${id}_p2.jpg", "test"))),
            Variant("${id}_p3", VariantKind.AltArt, images = listOf(ImageSource("https://img/missing.jpg", "test"))),
        ),
    )

    private inner class Files_(card: Card, dir: Path) {
        val png: Path = dir.resolve(card.setId).resolve("${card.id}.png")
        val jpg: Path = dir.resolve(card.setId).resolve("${card.id}.jpg")
        val small: Path = dir.resolve(card.setId).resolve("${card.id}_small.jpg")
    }
    private fun sim(card: Card) = Files_(card, cards)
    private fun backup(card: Card) = Files_(card, dirs.backups)

    private val stockPng = byteArrayOf(1, 1, 1)
    private val stockJpg = byteArrayOf(2, 2, 2)
    private val stockSmall = byteArrayOf(3, 3, 3)

    /** Older sets (OP01–OP08): stock art is `<ID>.png`. */
    private val pngCard = card("OP01-001").also { c ->
        sim(c).png.parent.createDirectories(); sim(c).png.writeBytes(stockPng); sim(c).small.writeBytes(stockSmall)
    }

    /** OP09 onwards: stock art is `<ID>.jpg`. */
    private val jpgCard = card("OP09-001").also { c ->
        sim(c).jpg.parent.createDirectories(); sim(c).jpg.writeBytes(stockJpg); sim(c).small.writeBytes(stockSmall)
    }

    private fun converted(card: Card, variant: Int) = converter.convertedFiles(card.setId, card.variants[variant].id)

    @Test
    fun jpgStockCardIsReplacedAndRestored() = runTest {
        art.apply(cards.toString(), jpgCard, jpgCard.variants[0])
        assertContentEquals(converted(jpgCard, 0).first.readBytes(), sim(jpgCard).jpg.readBytes())
        assertContentEquals(converted(jpgCard, 0).second.readBytes(), sim(jpgCard).small.readBytes())
        assertFalse(sim(jpgCard).png.exists(), "no .png is ever written")
        assertContentEquals(stockJpg, backup(jpgCard).jpg.readBytes())
        assertContentEquals(stockSmall, backup(jpgCard).small.readBytes())

        art.restoreOriginal(cards.toString(), jpgCard)
        assertContentEquals(stockJpg, sim(jpgCard).jpg.readBytes())
        assertContentEquals(stockSmall, sim(jpgCard).small.readBytes())
        assertNull(art.appliedVariant(jpgCard.id))
    }

    @Test
    fun pngStockCardGetsAJpgThatWinsAndLosesItOnRestore() = runTest {
        art.apply(cards.toString(), pngCard, pngCard.variants[0])
        assertContentEquals(converted(pngCard, 0).first.readBytes(), sim(pngCard).jpg.readBytes(), "the .jpg the sim prefers")
        assertContentEquals(stockPng, sim(pngCard).png.readBytes(), "stock .png untouched")
        assertContentEquals(stockPng, backup(pngCard).png.readBytes())
        assertFalse(backup(pngCard).jpg.exists(), "there was no stock .jpg to back up")

        art.restoreOriginal(cards.toString(), pngCard)
        assertFalse(sim(pngCard).jpg.exists(), "our .jpg is removed so the stock .png shows again")
        assertContentEquals(stockPng, sim(pngCard).png.readBytes())
        assertContentEquals(stockSmall, sim(pngCard).small.readBytes())
    }

    @Test
    fun appliedVariantsListsEveryChangedCard() = runTest {
        art.apply(cards.toString(), pngCard, pngCard.variants[0])
        art.apply(cards.toString(), jpgCard, jpgCard.variants[1])
        assertEquals(mapOf(pngCard.id to pngCard.variants[0].id, jpgCard.id to jpgCard.variants[1].id), art.appliedVariants())
        art.restoreOriginal(cards.toString(), pngCard)
        assertEquals(setOf(jpgCard.id), art.appliedVariants().keys)
    }

    @Test
    fun switchingVariantsNeverBacksUpOurOwnArt() = runTest {
        art.apply(cards.toString(), pngCard, pngCard.variants[0])
        art.apply(cards.toString(), pngCard, pngCard.variants[1])
        assertFalse(backup(pngCard).jpg.exists(), "the sim's .jpg was ours, not an original")
        assertContentEquals(stockSmall, backup(pngCard).small.readBytes())
        assertEquals(pngCard.variants[1].id, art.appliedVariant(pngCard.id))

        art.apply(cards.toString(), jpgCard, jpgCard.variants[0])
        art.apply(cards.toString(), jpgCard, jpgCard.variants[1])
        assertContentEquals(stockJpg, backup(jpgCard).jpg.readBytes())
    }

    @Test
    fun cardSwappedByHandTheOldWayIsRestoredFromItsPngBackup() = runTest {
        // Earlier hand test (EB01-006): stock .png backed up, sim .png overwritten, no selection recorded.
        val legacy = card("EB01-006")
        backup(legacy).png.parent.createDirectories()
        backup(legacy).png.writeBytes(stockPng)
        backup(legacy).small.writeBytes(stockSmall)
        sim(legacy).png.parent.createDirectories()
        sim(legacy).png.writeBytes(byteArrayOf(9, 9))
        sim(legacy).small.writeBytes(byteArrayOf(9, 9))

        art.apply(cards.toString(), legacy, legacy.variants[0])
        assertContentEquals(stockPng, backup(legacy).png.readBytes(), "existing backups are never overwritten")

        art.restoreOriginal(cards.toString(), legacy)
        assertContentEquals(stockPng, sim(legacy).png.readBytes())
        assertContentEquals(stockSmall, sim(legacy).small.readBytes())
        assertFalse(sim(legacy).jpg.exists())
    }

    @Test
    fun cardTheSimDidNotHaveIsRemovedAgainOnRestore() = runTest {
        val newCard = card("OP19-001")
        art.apply(cards.toString(), newCard, newCard.variants[0])
        assertTrue(sim(newCard).jpg.exists())
        art.restoreOriginal(cards.toString(), newCard)
        assertFalse(sim(newCard).jpg.exists())
        assertFalse(sim(newCard).small.exists())
    }

    @Test
    fun brokenArtCantBeAppliedAndLeavesTheSimUntouched() = runTest {
        val error = assertFailsWith<IllegalStateException> { art.apply(cards.toString(), jpgCard, jpgCard.variants[2]) }
        assertTrue(error.message!!.contains("couldn't be downloaded"), error.message)
        assertContentEquals(stockJpg, sim(jpgCard).jpg.readBytes())
        assertNull(art.appliedVariant(jpgCard.id))
        assertNull(art.variantPreview(jpgCard, jpgCard.variants[2]))
    }

    @Test
    fun previewsShowTheConvertedVariantAndTheOriginal() = runTest {
        val preview = assertNotNull(art.variantPreview(jpgCard, jpgCard.variants[0]))
        assertEquals(480 to 671, preview.width to preview.height)

        val stock = encoded(Color.RED, 120, 167, "jpg")
        sim(jpgCard).jpg.writeBytes(stock)
        assertEquals(120, art.originalPreview(cards.toString(), jpgCard)?.width)
        art.apply(cards.toString(), jpgCard, jpgCard.variants[0])
        assertEquals(120, art.originalPreview(cards.toString(), jpgCard)?.width, "the backup, not our applied 480px art")

        // PNG-stock card: once changed, the sim's .jpg is ours, so the original is the stock .png.
        sim(pngCard).png.writeBytes(encoded(Color.RED, 100, 140, "png"))
        art.apply(cards.toString(), pngCard, pngCard.variants[0])
        assertEquals(100, art.originalPreview(cards.toString(), pngCard)?.width)
    }
}
