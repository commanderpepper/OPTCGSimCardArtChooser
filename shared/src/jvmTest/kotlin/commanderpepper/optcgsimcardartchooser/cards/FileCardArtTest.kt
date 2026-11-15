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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
        val url = request.url.toString()
        when {
            "missing" in url -> respond("nope", HttpStatusCode.NotFound)
            "page" in url -> respond("<html></html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))
            else -> respond(encoded(Color.GREEN, 600, 838, "png"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png"))
        }
    })
    private val converter = SetConverter(http, dirs, requestDelayMillis = 0)
    private val art = FileCardArt(dirs, converter, http)

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
    fun reapplyLeavesUntouchedCardsAlone() = runTest {
        art.apply(cards.toString(), jpgCard, jpgCard.variants[0])
        assertEquals(ReapplyOutcome.AlreadyApplied, art.reapply(cards.toString(), jpgCard, jpgCard.variants[0]))
        assertContentEquals(stockJpg, backup(jpgCard).jpg.readBytes())
    }

    @Test
    fun reapplyAfterASimUpdatePutsOurArtBackAndKeepsTheUpdatedArtAsTheOriginal() = runTest {
        art.apply(cards.toString(), jpgCard, jpgCard.variants[0])
        // The sim update overwrites both files with new stock art.
        val updatedJpg = byteArrayOf(7, 7, 7)
        val updatedSmall = byteArrayOf(8, 8, 8)
        sim(jpgCard).jpg.writeBytes(updatedJpg)
        sim(jpgCard).small.writeBytes(updatedSmall)

        assertEquals(ReapplyOutcome.Reapplied, art.reapply(cards.toString(), jpgCard, jpgCard.variants[0]))
        assertContentEquals(converted(jpgCard, 0).first.readBytes(), sim(jpgCard).jpg.readBytes())
        assertContentEquals(converted(jpgCard, 0).second.readBytes(), sim(jpgCard).small.readBytes())
        assertContentEquals(updatedJpg, backup(jpgCard).jpg.readBytes(), "backup is now the updated stock art")
        assertContentEquals(updatedSmall, backup(jpgCard).small.readBytes())

        art.restoreOriginal(cards.toString(), jpgCard)
        assertContentEquals(updatedJpg, sim(jpgCard).jpg.readBytes(), "restore brings back the updated art, not the old one")
    }

    @Test
    fun reapplyForAPngCardRefreshesOnlyWhatTheUpdateChanged() = runTest {
        art.apply(cards.toString(), pngCard, pngCard.variants[0])
        // The update ships a new .png and _small.jpg but leaves our .jpg in place.
        val updatedPng = byteArrayOf(5, 5, 5)
        sim(pngCard).png.writeBytes(updatedPng)
        sim(pngCard).small.writeBytes(byteArrayOf(6, 6, 6))

        assertEquals(ReapplyOutcome.Reapplied, art.reapply(cards.toString(), pngCard, pngCard.variants[0]))
        assertContentEquals(converted(pngCard, 0).second.readBytes(), sim(pngCard).small.readBytes())
        assertContentEquals(updatedPng, backup(pngCard).png.readBytes())
        assertFalse(backup(pngCard).jpg.exists(), "our own .jpg is never backed up")
    }

    private fun image(name: String, width: Int = 800, height: Int = 1100, format: String = "png", color: Color = Color.ORANGE): String =
        root.resolve(name).also { it.writeBytes(encoded(color, width, height, format)) }.toString()

    @Test
    fun customImageIsConvertedToTheSimSpecAndAppliedLikeAnyVariant() = runTest {
        val (added, info) = art.addCustomImage(jpgCard, image("my art.png"))
        assertEquals("OP09-001_custom", added.variant.id)
        assertEquals(800 to 1100, info.width to info.height)
        assertFalse(info.isLowResolution)

        val stored = dirs.data.resolve("custom/OP09/OP09-001_custom.jpg")
        assertEquals(480 to 671, ImageIO.read(stored.toFile()).let { it.width to it.height })
        assertTrue(dirs.data.resolve("custom/OP09/OP09-001_custom_small.jpg").exists(), "thumbnail made too")
        assertEquals(480, added.preview?.width)

        art.apply(cards.toString(), jpgCard, added.variant)
        assertContentEquals(stored.readBytes(), sim(jpgCard).jpg.readBytes())
        assertContentEquals(stockJpg, backup(jpgCard).jpg.readBytes())
        assertEquals("OP09-001_custom", art.appliedVariant(jpgCard.id))

        // After a sim update, reapply works from the stored custom files.
        sim(jpgCard).jpg.writeBytes(byteArrayOf(7, 7, 7))
        assertEquals(ReapplyOutcome.Reapplied, art.reapply(cards.toString(), jpgCard, CustomArt.resolve(jpgCard, "OP09-001_custom")!!))
        assertContentEquals(stored.readBytes(), sim(jpgCard).jpg.readBytes())
    }

    @Test
    fun aCardCanHaveSeveralCustomImages() = runTest {
        val first = art.addCustomImage(jpgCard, image("1.png", color = Color.RED)).first
        val second = art.addCustomImage(jpgCard, image("2.jpg", format = "jpg", color = Color.BLUE)).first
        val third = art.addCustomImage(jpgCard, image("3.png", color = Color.GREEN)).first
        assertEquals(listOf("OP09-001_custom", "OP09-001_custom2", "OP09-001_custom3"), listOf(first, second, third).map { it.variant.id })
        assertEquals(listOf("OP09-001_custom", "OP09-001_custom2", "OP09-001_custom3"), art.customImages(jpgCard).map { it.variant.id })
        assertTrue(art.customImages(pngCard).isEmpty(), "images belong to their card")

        art.apply(cards.toString(), jpgCard, second.variant)
        val pixel = Color(ImageIO.read(sim(jpgCard).jpg.toFile()).getRGB(240, 335))
        assertTrue(pixel.blue > 200 && pixel.red < 60, "the second (blue) image is in the sim, got $pixel")

        // Removing one keeps the rest, and new images never reuse a taken number.
        art.removeCustomImage(jpgCard, first.variant.id)
        assertEquals(listOf("OP09-001_custom2", "OP09-001_custom3"), art.customImages(jpgCard).map { it.variant.id })
        assertEquals("OP09-001_custom4", art.addCustomImage(jpgCard, image("4.png")).first.variant.id)
    }

    @Test
    fun replacingKeepsTheIdAndChangesThePicture() = runTest {
        val id = art.addCustomImage(jpgCard, image("red.png", color = Color.RED)).first.variant.id
        val (replaced, _) = art.replaceCustomImage(jpgCard, id, image("blue.png", color = Color.BLUE))
        assertEquals(id, replaced.variant.id)
        val pixel = Color(ImageIO.read(dirs.data.resolve("custom/OP09/$id.jpg").toFile()).getRGB(240, 335))
        assertTrue(pixel.blue > 200 && pixel.red < 60, "now blue, got $pixel")
        assertEquals(1, art.customImages(jpgCard).size)
    }

    @Test
    fun customImageFromTheSingleImageVersionIsKept() = runTest {
        // The previous version stored one image per card as custom/<SET>/<ID>.jpg.
        val dir = dirs.data.resolve("custom/OP09").also { it.createDirectories() }
        dir.resolve("OP09-001.jpg").writeBytes(encoded(Color.RED, 480, 671, "jpg"))
        dir.resolve("OP09-001_small.jpg").writeBytes(encoded(Color.RED, 120, 167, "jpg"))

        assertEquals(listOf("OP09-001_custom"), art.customImages(jpgCard).map { it.variant.id })
        assertTrue(dir.resolve("OP09-001_custom.jpg").exists())
        assertFalse(dir.resolve("OP09-001.jpg").exists())
        art.apply(cards.toString(), jpgCard, CustomArt.variant("OP09-001_custom")) // a saved selection still applies
    }

    @Test
    fun jpgCustomImagesWorkAndSmallOnesAreFlagged() = runTest {
        val (_, info) = art.addCustomImage(pngCard, image("small.JPG", width = 240, height = 335, format = "jpg"))
        assertTrue(info.isLowResolution)
        assertEquals(1, art.customImages(pngCard).size)
    }

    @Test
    fun unsupportedOrBrokenFilesAreRejected() = runTest {
        val gif = root.resolve("art.gif").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(assertFailsWith<IllegalStateException> { art.addCustomImage(jpgCard, gif.toString()) }.message!!.contains("only JPG, PNG and WebP"))
        val fake = root.resolve("art.png").also { it.writeBytes("not really a png".encodeToByteArray()) }
        assertTrue(assertFailsWith<IllegalStateException> { art.addCustomImage(jpgCard, fake.toString()) }.message!!.contains("isn't a readable"))
        assertTrue(art.customImages(jpgCard).isEmpty())
    }

    @Test
    fun removedCustomImageCantBeApplied() = runTest {
        val variant = art.addCustomImage(jpgCard, image("a.png")).first.variant
        art.removeCustomImage(jpgCard, variant.id)
        val error = assertFailsWith<IllegalStateException> { art.apply(cards.toString(), jpgCard, variant) }
        assertTrue(error.message!!.contains("custom image is missing"), error.message)
        assertContentEquals(stockJpg, sim(jpgCard).jpg.readBytes())
    }

    @Test
    fun imageDroppedFromABrowserIsDownloadedAndConverted() = runTest {
        // Browser drops arrive as the image's address, often without a file extension.
        val (image, info) = art.addCustomImage(jpgCard, "https://images.example/art?id=42")
        assertEquals("OP09-001_custom", image.variant.id)
        assertEquals(600 to 838, info.width to info.height)
        assertEquals(480 to 671, ImageIO.read(dirs.data.resolve("custom/OP09/OP09-001_custom.jpg").toFile()).let { it.width to it.height })
    }

    /** A real WebP file, made with Skia (the same library that decodes them in the app). */
    private fun webp(width: Int, height: Int, lossless: Boolean = false): ByteArray {
        val bitmap = org.jetbrains.skia.Bitmap().apply {
            allocPixels(org.jetbrains.skia.ImageInfo.makeN32Premul(width, height))
            erase(0xFF2266CC.toInt())
        }
        return org.jetbrains.skia.Image.makeFromBitmap(bitmap)
            .encodeToData(org.jetbrains.skia.EncodedImageFormat.WEBP, if (lossless) 100 else 80)!!.bytes
    }

    @Test
    fun webpFilesAreConvertedLikeAnyOtherImage() = runTest {
        val file = root.resolve("art.webp").also { it.writeBytes(webp(900, 1260)) }
        val (image, info) = art.addCustomImage(jpgCard, file.toString())
        assertEquals(900 to 1260, info.width to info.height)
        val stored = ImageIO.read(dirs.data.resolve("custom/OP09/${image.variant.id}.jpg").toFile())
        assertEquals(480 to 671, stored.width to stored.height)
        val pixel = Color(stored.getRGB(240, 335))
        assertTrue(pixel.blue > 170 && pixel.red < 80, "kept its colour through the conversion, got $pixel")

        val lossless = root.resolve("lossless.WEBP").also { it.writeBytes(webp(480, 671, lossless = true)) }
        assertEquals(480 to 671, art.addCustomImage(jpgCard, lossless.toString()).second.let { it.width to it.height })
    }

    @Test
    fun webpImagesDroppedFromABrowserAreDownloadedAndConverted() = runTest {
        // Many sites serve WebP; the address often has no extension at all.
        val webpHttp = HttpClient(MockEngine { respond(webp(600, 838), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/webp")) })
        val webpArt = FileCardArt(dirs, converter, webpHttp)
        val (_, info) = webpArt.addCustomImage(jpgCard, "https://cdn.example/img/12345")
        assertEquals(600 to 838, info.width to info.height)
    }

    @Test
    fun brokenWebpIsRejectedClearly() = runTest {
        // A RIFF/WEBP header with garbage after it.
        val bytes = "RIFF\u0000\u0000\u0000\u0000WEBPgarbage!".encodeToByteArray()
        val file = root.resolve("broken.webp").also { it.writeBytes(bytes) }
        val error = assertFailsWith<IllegalStateException> { art.addCustomImage(jpgCard, file.toString()) }
        assertTrue(error.message!!.contains("WebP image couldn't be read"), error.message)
    }

    @Test
    fun inlineDataImagesFromABrowserWork() = runTest {
        val base64 = java.util.Base64.getEncoder().encodeToString(encoded(Color.BLUE, 300, 420, "png"))
        val (_, info) = art.addCustomImage(jpgCard, "data:image/png;base64,$base64")
        assertEquals(300 to 420, info.width to info.height)
    }

    @Test
    fun linksToPagesOrMissingImagesAreRejectedClearly() = runTest {
        assertTrue(assertFailsWith<IllegalStateException> { art.addCustomImage(jpgCard, "https://site.example/page") }.message!!.contains("web page, not an image"))
        assertTrue(assertFailsWith<IllegalStateException> { art.addCustomImage(jpgCard, "https://site.example/missing.png") }.message!!.contains("HTTP 404"))
        assertTrue(art.customImages(jpgCard).isEmpty())
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
