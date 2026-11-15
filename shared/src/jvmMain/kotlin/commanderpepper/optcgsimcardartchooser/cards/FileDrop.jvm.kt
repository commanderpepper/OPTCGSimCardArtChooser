package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.toPath

/**
 * Accepts images dropped from Explorer/Finder and from browsers. Browsers don't hand over real
 * files: an image dragged from a page carries a virtual file Java can't read (and sometimes a
 * file list it can't deliver — AWT's "no native data was transferred"), its address as a URL /
 * `text/uri-list`, an HTML snippet with the `<img src>`, and sometimes the on-screen bitmap.
 * So the drop yields local paths where possible, otherwise the image's web address (downloaded
 * later, off the UI thread). Reading a drop never throws; failures go to [onDropFailed].
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.fileDropTarget(
    enabled: Boolean,
    onHoverChange: (Boolean) -> Unit,
    onDrop: (List<String>) -> Unit,
    onDropFailed: () -> Unit,
): Modifier {
    val currentHover by rememberUpdatedState(onHoverChange)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentFailed by rememberUpdatedState(onDropFailed)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = currentHover(true)
            override fun onExited(event: DragAndDropEvent) = currentHover(false)
            override fun onEnded(event: DragAndDropEvent) = currentHover(false)

            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentHover(false)
                val transferable = event.awtTransferable
                logOfferedFormats(transferable)
                val sources = runCatching { droppedSources(transferable) }.getOrNull().orEmpty()
                if (sources.isEmpty()) {
                    currentFailed()
                    return false
                }
                currentDrop(sources)
                return true
            }
        }
    }
    return if (enabled) {
        dragAndDropTarget(shouldStartDragAndDrop = { it.awtTransferable.offersSomethingUsable() }, target = target)
    } else {
        this
    }
}

private val URI_LIST = DataFlavor("text/uri-list;class=java.lang.String")
private val JAVA_URL = DataFlavor("application/x-java-url;class=java.net.URL")
private val HTML = DataFlavor("text/html;class=java.lang.String")
private val IMG_SRC = Regex("""<img\b[^>]*?\bsrc\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)

private fun Transferable.offersSomethingUsable(): Boolean = runCatching {
    listOf(DataFlavor.javaFileListFlavor, URI_LIST, JAVA_URL, HTML, DataFlavor.imageFlavor).any(::isDataFlavorSupported)
}.getOrDefault(false)

/**
 * What was dropped, as local file paths or (for browser drags) image addresses: `http(s):` or
 * `data:` URIs. Tries each format the source offers, best first; empty if none can be read.
 */
internal fun droppedSources(transferable: Transferable): List<String> {
    // 1. Real files (Explorer, Finder, most file managers).
    readOrNull { (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)?.filterIsInstance<File>() }
        ?.takeIf { it.isNotEmpty() }
        ?.let { files -> return files.map { it.absolutePath } }

    val uris = readOrNull { transferable.getTransferData(URI_LIST) as? String }
        ?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }?.toList().orEmpty()

    // 2. file: links (some apps and Linux file managers).
    uris.filter { it.startsWith("file:") }
        .mapNotNull { runCatching { URI(it).toPath().toString() }.getOrNull() }
        .takeIf { it.isNotEmpty() }
        ?.let { return it }

    // 3. Browser drags: the <img src> is the image itself, even when the image is inside a link
    //    (then the URL list holds the link's target, not the image).
    readOrNull { transferable.getTransferData(HTML) as? String }
        ?.let { html -> IMG_SRC.find(html)?.groupValues?.get(2)?.let(::unescapeHtml) }
        ?.takeIf(::isRemoteImageSource)
        ?.let { return listOf(it) }

    // 4. The dragged item's web address.
    (listOfNotNull(readOrNull { (transferable.getTransferData(JAVA_URL) as? URL)?.toString() }) + uris)
        .firstOrNull(::isRemoteImageSource)
        ?.let { return listOf(it) }

    // 5. The bitmap itself, as rendered (lowest quality, but needs no download).
    readOrNull { transferable.getTransferData(DataFlavor.imageFlavor) as? Image }
        ?.let(::saveAsTempPng)
        ?.let { return listOf(it) }

    return emptyList()
}

private fun isRemoteImageSource(source: String): Boolean =
    source.startsWith("https://", ignoreCase = true) || source.startsWith("http://", ignoreCase = true) ||
        source.startsWith("data:image/", ignoreCase = true)

private fun unescapeHtml(text: String): String =
    text.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

private inline fun <T> readOrNull(read: () -> T?): T? = try {
    read()
} catch (_: Exception) {
    null // UnsupportedFlavorException, or IOException "no native data was transferred".
}

private fun saveAsTempPng(image: Image): String? {
    val width = image.getWidth(null)
    val height = image.getHeight(null)
    if (width <= 0 || height <= 0) return null
    val rgb = image as? BufferedImage ?: BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
        val g = it.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.dispose()
    }
    val file = Files.createTempFile("optcgsim-dropped-image", ".png").toFile().apply { deleteOnExit() }
    return if (ImageIO.write(rgb, "png", file)) file.absolutePath else null
}

/** One console line per drop listing what the source offered, to diagnose apps that still fail. */
private fun logOfferedFormats(transferable: Transferable) {
    val types = runCatching { transferable.transferDataFlavors.map { it.mimeType.substringBefore(';') }.distinct() }.getOrNull()
    System.err.println("[drop] offered: ${types?.joinToString(", ") ?: "unknown"}")
}
