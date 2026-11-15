package commanderpepper.optcgsimcardartchooser.catalog.conversion

import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardSet
import commanderpepper.optcgsimcardartchooser.catalog.model.Catalog
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
import kotlin.io.path.name
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

    private val catalog = Catalog(
        schemaVersion = 2, generatedAt = "t",
        sets = listOf(CardSet("OP01", "Romance Dawn"), CardSet("OP02", "Paramount War")),
        cards = listOf(
            Card(
                "OP01-001", "Zoro", "OP01", Category.Leader, emptyList(),
                variants = listOf(
                    variant("OP01-001", VariantKind.Regular, emptyList(), "https://img/OP01-001.jpg"),
                    variant("OP01-001_p1", VariantKind.AltArt, listOf("Parallel"), "https://img/missing.jpg", "https://img/OP01-001_p1.jpg"),
                    variant("OP01-001_p2", VariantKind.Foil, listOf("Jolly Roger Foil"), "https://img/jolly.jpg"),
                    variant("OP01-001_r1", VariantKind.Reprint, listOf("Reprint"), "https://img/reprint.jpg"),
                ),
            ),
            Card(
                "OP01-002", "Law", "OP01", Category.Leader, emptyList(),
                variants = listOf(variant("OP01-002", VariantKind.Regular, emptyList(), "https://img/html.jpg")),
            ),
            Card(
                "OP02-001", "Newgate", "OP02", Category.Leader, emptyList(),
                variants = listOf(variant("OP02-001", VariantKind.Regular, emptyList(), "https://img/OP02-001.jpg")),
            ),
        ),
    )

    private suspend fun run() = SetConverter(http, dirs, requestDelayMillis = 0).convert(catalog, "OP01", finishedAt = { "now" })

    @Test
    fun convertsOnlyInScopeVariantsOfTheRequestedSet() = runTest {
        val report = run()
        val statuses = report.results.associate { it.variantId to it.status }
        assertEquals(
            mapOf(
                "OP01-001" to VariantResult.Status.Converted,
                "OP01-001_p1" to VariantResult.Status.Converted,
                "OP01-002" to VariantResult.Status.Failed,
            ),
            statuses,
        )
        assertTrue(requested.none { "jolly" in it || "reprint" in it || "OP02" in it }, requested.toString())

        val out = dirs.convertedSet("OP01")
        assertEquals(
            setOf("OP01-001.jpg", "OP01-001_small.jpg", "OP01-001_p1.jpg", "OP01-001_p1_small.jpg"),
            out.listDirectoryEntries().map { it.name }.toSet(),
        )
        val full = ImageIO.read(out.resolve("OP01-001_p1.jpg").toFile())
        assertEquals(480 to 671, full.width to full.height)
    }

    @Test
    fun fallsBackToTheNextImageUrlAndCachesDownloadsByContentType() = runTest {
        run()
        assertTrue(requested.indexOf("https://img/missing.jpg") < requested.indexOf("https://img/OP01-001_p1.jpg"))
        // Served bytes are PNG even though the URLs end in .jpg.
        assertTrue(dirs.downloads.resolve("OP01-001_p1.png").exists())
    }

    @Test
    fun failuresExplainWhy() = runTest {
        val failed = run().results.single { it.status == VariantResult.Status.Failed }
        assertTrue(failed.message!!.contains("not an image"), failed.message)
    }

    @Test
    fun rerunSkipsFinishedVariantsWithoutNetwork() = runTest {
        run()
        requested.clear()
        val second = run()
        assertEquals(2, second.count(VariantResult.Status.AlreadyConverted))
        assertEquals(listOf("https://img/html.jpg"), requested, "only the previously failed variant is retried")
    }

    @Test
    fun conversionReusesCachedDownloads() = runTest {
        run()
        dirs.convertedSet("OP01").resolve("OP01-001.jpg").toFile().delete()
        requested.clear()
        val again = run()
        assertEquals(VariantResult.Status.Converted, again.results.first { it.variantId == "OP01-001" }.status)
        assertTrue(requested.none { "OP01-001.jpg" in it }, "re-converted from the cached download")
    }
}
