package commanderpepper.optcgsimcardartchooser.catalog.conversion

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
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetConverterTest {
    private val root = Files.createTempDirectory("set-converter-test")
    private val dirs = AppDirectories(data = root.resolve("data"), cache = root.resolve("cache"))
    private val requested = mutableListOf<String>()

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private val pngBytes: ByteArray = BufferedImage(600, 838, BufferedImage.TYPE_INT_RGB).let { img ->
        img.createGraphics().apply { color = Color.ORANGE; fillRect(0, 0, 600, 838); dispose() }
        ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    private val http = HttpClient(MockEngine { request ->
        val url = request.url.toString()
        requested += url
        when {
            "missing" in url -> respond("not found", HttpStatusCode.NotFound)
            "html" in url -> respond("<html>error</html>", HttpStatusCode.OK)
            else -> respond(pngBytes, HttpStatusCode.OK)
        }
    })

    private fun variant(id: String, kind: VariantKind, tags: List<String> = emptyList(), vararg urls: String) =
        Variant(id, kind, tags, images = urls.map { ImageSource(it, "test") })

    private val zoro = Card(
        "OP01-001", "Zoro", "OP01", Category.Leader, emptyList(),
        variants = listOf(
            variant("OP01-001", VariantKind.Regular, emptyList(), "https://img/OP01-001.jpg"),
            variant("OP01-001_p1", VariantKind.AltArt, listOf("Parallel"), "https://img/missing.jpg", "https://img/OP01-001_p1.jpg"),
        ),
    )
    private val law = Card(
        "OP01-002", "Law", "OP01", Category.Leader, emptyList(),
        variants = listOf(variant("OP01-002", VariantKind.Regular, emptyList(), "https://img/html.jpg")),
    )

    private val converter = SetConverter(http, dirs, requestDelayMillis = 0)
    private suspend fun convert(card: Card, index: Int = 0) = converter.convertVariant(card, card.variants[index])

    @Test
    fun writesSimReadyFullAndSmallJpegs() = runTest {
        assertEquals(VariantResult.Status.Converted, convert(zoro, 1).status)
        val (full, small) = converter.convertedFiles("OP01", "OP01-001_p1")
        assertEquals(dirs.convertedSet("OP01"), full.parent)
        val fullImage = ImageIO.read(full.toFile())
        assertEquals(480 to 671, fullImage.width to fullImage.height)
        val smallImage = ImageIO.read(small.toFile())
        assertEquals(120 to 167, smallImage.width to smallImage.height)
    }

    @Test
    fun fallsBackToTheNextImageUrlAndCachesDownloadsByContentType() = runTest {
        convert(zoro, 1)
        assertTrue(requested.indexOf("https://img/missing.jpg") < requested.indexOf("https://img/OP01-001_p1.jpg"))
        // Served bytes are PNG even though the URLs end in .jpg.
        assertTrue(dirs.downloads.resolve("OP01-001_p1.png").exists())
    }

    @Test
    fun failuresExplainWhy() = runTest {
        val failed = convert(law)
        assertEquals(VariantResult.Status.Failed, failed.status)
        assertTrue(failed.message!!.contains("not an image"), failed.message)
        assertTrue(dirs.convertedSet("OP01").listDirectoryEntries().isEmpty(), "nothing written for a failure")
    }

    @Test
    fun alreadyConvertedVariantsSkipTheNetwork() = runTest {
        convert(zoro)
        requested.clear()
        assertEquals(VariantResult.Status.AlreadyConverted, convert(zoro).status)
        assertTrue(requested.isEmpty(), requested.toString())
    }

    @Test
    fun conversionReusesCachedDownloads() = runTest {
        convert(zoro)
        converter.convertedFiles("OP01", "OP01-001").first.toFile().delete()
        requested.clear()
        assertEquals(VariantResult.Status.Converted, convert(zoro).status)
        assertTrue(requested.isEmpty(), "re-converted from the cached download")
    }
}
