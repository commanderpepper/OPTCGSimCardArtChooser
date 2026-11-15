package commanderpepper.optcgsimcardartchooser.cards

import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.Category
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind
import kotlinx.coroutines.test.runTest
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class FileCardThumbnailsTest {
    private val root: Path = Files.createTempDirectory("thumbnails-test")
    private val cards = root.resolve("sim/Cards")
    private val converted = root.resolve("app/converted")
    private val thumbnails = FileCardThumbnails(converted)

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun card(id: String) = Card(id, "name", id.substringBefore('-'), Category.Character, emptyList(), variants = listOf(Variant(id, VariantKind.Regular)))

    private fun writeJpeg(dir: Path, name: String, width: Int, color: Color) {
        val image = BufferedImage(width, 167, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { this.color = color; fillRect(0, 0, width, 167); dispose() }
        ImageIO.write(image, "jpg", dir.createDirectories().resolve(name).toFile())
    }

    @Test
    fun prefersTheSimsOwnThumbnail() = runTest {
        writeJpeg(cards.resolve("OP01"), "OP01-001_small.jpg", width = 120, color = Color.RED)
        writeJpeg(converted.resolve("OP01"), "OP01-001_small.jpg", width = 100, color = Color.BLUE)
        val image = thumbnails.load(cards.toString(), card("OP01-001"))
        assertEquals(120, image?.width, "the sim's file (120 wide), not the converted one (100 wide)")
    }

    @Test
    fun fallsBackToTheConvertedCopy() = runTest {
        writeJpeg(converted.resolve("OP18"), "OP18-001_small.jpg", width = 100, color = Color.BLUE)
        assertEquals(100, thumbnails.load(cards.toString(), card("OP18-001"))?.width)
    }

    @Test
    fun missingOrCorruptImagesGivePlaceholderNull() = runTest {
        assertNull(thumbnails.load(cards.toString(), card("OP01-999")))
        cards.resolve("OP01").createDirectories().resolve("OP01-002_small.jpg").writeText("not a jpeg")
        assertNull(thumbnails.load(cards.toString(), card("OP01-002")))
    }

    @Test
    fun decodedImagesAreCached() = runTest {
        writeJpeg(cards.resolve("OP01"), "OP01-001_small.jpg", width = 120, color = Color.RED)
        val first = thumbnails.load(cards.toString(), card("OP01-001"))
        Files.delete(cards.resolve("OP01/OP01-001_small.jpg"))
        assertSame(first, thumbnails.load(cards.toString(), card("OP01-001")))
    }
}
