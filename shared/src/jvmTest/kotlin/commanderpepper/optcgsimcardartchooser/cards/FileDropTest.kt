package commanderpepper.optcgsimcardartchooser.cards

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.net.URI
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileDropTest {
    private val uriList = DataFlavor("text/uri-list;class=java.lang.String")
    private val javaUrl = DataFlavor("application/x-java-url;class=java.net.URL")
    private val html = DataFlavor("text/html;class=java.lang.String")

    /** The reported crash: a file list is advertised but reading it fails. */
    private val noNativeData = IOException("no native data was transferred")

    /** Offers [data] per flavor; a value that is an Exception is thrown when read, like AWT does. */
    private class FakeTransferable(private val data: Map<DataFlavor, Any>) : Transferable {
        override fun getTransferDataFlavors() = data.keys.toTypedArray()
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor in data
        override fun getTransferData(flavor: DataFlavor): Any {
            val value = data[flavor] ?: throw UnsupportedFlavorException(flavor)
            if (value is Exception) throw value
            return value
        }
    }

    private fun sources(vararg data: Pair<DataFlavor, Any>) = droppedSources(FakeTransferable(mapOf(*data)))

    @Test
    fun fileListFromExplorerIsUsed() {
        val files = listOf(File("C:/art/a.png"), File("C:/art/b.jpg"))
        assertEquals(files.map { it.absolutePath }, sources(DataFlavor.javaFileListFlavor to files))
    }

    @Test
    fun browserImageDragYieldsTheImageAddressFromItsHtml() {
        // Roughly what a browser offers for an image wrapped in a link: the URL list holds the link's target.
        val result = sources(
            DataFlavor.javaFileListFlavor to noNativeData,
            uriList to "https://site.example/gallery/page-7\r\n",
            javaUrl to URI("https://site.example/gallery/page-7").toURL(),
            html to """<html><body><a href="/gallery/page-7"><img alt="art" src="https://cdn.example/art/luffy.png?w=1200&amp;q=90"></a></body></html>""",
        )
        assertEquals(listOf("https://cdn.example/art/luffy.png?w=1200&q=90"), result, "the image, not the link; HTML entities decoded")
    }

    @Test
    fun withoutHtmlTheDraggedAddressIsUsed() {
        assertEquals(
            listOf("https://cdn.example/art/zoro.jpg"),
            sources(DataFlavor.javaFileListFlavor to noNativeData, javaUrl to URI("https://cdn.example/art/zoro.jpg").toURL()),
        )
        assertEquals(listOf("https://cdn.example/a.png"), sources(uriList to "# comment\nhttps://cdn.example/a.png\n"))
    }

    @Test
    fun inlineDataImagesInHtmlAreKept() {
        val src = "data:image/png;base64,iVBORw0KGgo="
        assertEquals(listOf(src), sources(html to """<img src='$src'>"""))
    }

    @Test
    fun relativeImageAddressesFallBackToOtherFormats() {
        // A relative src can't be downloaded on its own; the bitmap is used instead.
        val result = sources(html to """<img src="/img/a.png">""", DataFlavor.imageFlavor to BufferedImage(600, 838, BufferedImage.TYPE_INT_RGB))
        val saved = File(result.single())
        assertTrue(saved.name.endsWith(".png"))
        assertEquals(600, ImageIO.read(saved).width)
    }

    @Test
    fun localFileLinksBecomePaths() {
        val dir = File(System.getProperty("java.io.tmpdir"))
        assertEquals(listOf(File(dir, "x.png").absolutePath), sources(uriList to "${File(dir, "x.png").toURI()}\r\n"))
    }

    @Test
    fun nothingReadableGivesNoSources() {
        assertTrue(sources(DataFlavor.javaFileListFlavor to noNativeData).isEmpty())
        assertTrue(sources(DataFlavor.stringFlavor to "hello").isEmpty())
        assertTrue(sources(uriList to "mailto:someone@example.com").isEmpty())
    }
}
