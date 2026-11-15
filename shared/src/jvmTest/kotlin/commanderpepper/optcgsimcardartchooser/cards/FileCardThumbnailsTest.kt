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
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FileCardThumbnailsTest {
    private val root: Path = Files.createTempDirectory("thumbnails-test")
    private val cards = root.resolve("sim/Cards")
    private val converted = root.resolve("app/converted")
    private val generated = root.resolve("app/cache/thumbnails")
    private val thumbnails = FileCardThumbnails(converted, generated)

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun card(id: String) = Card(id, "name", id.substringBefore('-'), Category.Character, emptyList(), variants = listOf(Variant(id, VariantKind.Regular)))

    private fun writeJpeg(dir: Path, name: String, width: Int, color: Color, height: Int = 167, format: String = "jpg") {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { this.color = color; fillRect(0, 0, width, height); dispose() }
        ImageIO.write(image, format, dir.createDirectories().resolve(name).toFile())
    }

    @Test
    fun missingThumbnailIsGeneratedFromTheFullImageInTheAppCache() = runTest {
        // Like EB05-033: the sim has the full image but no _small.jpg.
        writeJpeg(cards.resolve("EB05"), "EB05-033.jpg", width = 480, height = 671, color = Color.RED)
        val image = thumbnails.load(cards.toString(), card("EB05-033"))
        assertEquals(120 to 167, image?.width to image?.height)
        assertTrue(generated.resolve("EB05/EB05-033_small.jpg").isRegularFile(), "saved in the app's cache")
        assertFalse(cards.resolve("EB05/EB05-033_small.jpg").exists(), "the sim folder isn't written to")
    }

    @Test
    fun generatedThumbnailPrefersTheJpgLikeTheSim() = runTest {
        writeJpeg(cards.resolve("OP05"), "OP05-098.png", width = 480, height = 671, color = Color.BLUE, format = "png")
        writeJpeg(cards.resolve("OP05"), "OP05-098.jpg", width = 480, height = 671, color = Color.RED)
        thumbnails.load(cards.toString(), card("OP05-098"))
        val pixel = Color(ImageIO.read(generated.resolve("OP05/OP05-098_small.jpg").toFile()).getRGB(60, 80))
        assertTrue(pixel.red > 200 && pixel.blue < 60, "made from the red .jpg, got $pixel")
    }

    @Test
    fun generatedThumbnailIsRemadeWhenTheFullImageChanges() = runTest {
        val full = cards.resolve("EB05/EB05-033.jpg")
        writeJpeg(cards.resolve("EB05"), "EB05-033.jpg", width = 480, height = 671, color = Color.RED)
        thumbnails.load(cards.toString(), card("EB05-033"))
        val saved = generated.resolve("EB05/EB05-033_small.jpg")
        Files.setLastModifiedTime(saved, FileTime.fromMillis(1_000))

        writeJpeg(cards.resolve("EB05"), "EB05-033.jpg", width = 480, height = 671, color = Color.BLUE) // e.g. a patch
        Files.setLastModifiedTime(full, FileTime.fromMillis(2_000))
        thumbnails.invalidate("EB05-033")
        thumbnails.load(cards.toString(), card("EB05-033"))
        val pixel = Color(ImageIO.read(saved.toFile()).getRGB(60, 80))
        assertTrue(pixel.blue > 200 && pixel.red < 60, "regenerated from the new image, got $pixel")
    }

    @Test
    fun corruptFullImageFallsThroughToThePlaceholder() = runTest {
        cards.resolve("EB05").createDirectories().resolve("EB05-034.jpg").writeText("not an image")
        assertNull(thumbnails.load(cards.toString(), card("EB05-034")))
        assertFalse(generated.resolve("EB05/EB05-034_small.jpg").exists())
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
