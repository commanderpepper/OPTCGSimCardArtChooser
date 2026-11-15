package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import commanderpepper.optcgsimcardartchooser.catalog.conversion.AppDirectories
import commanderpepper.optcgsimcardartchooser.catalog.image.SimCardImages
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readBytes

/**
 * Thumbnails from disk, in order of preference:
 * 1. the sim's own `<Cards>/<SET>/<ID>_small.jpg` — the art the sim currently shows;
 * 2. one generated from the sim's full image (`<ID>.jpg`, else `<ID>.png`) when the sim has no
 *    thumbnail, saved in the app's cache (`thumbnails/<SET>/<ID>_small.jpg`) and regenerated if
 *    the full image changes. The sim folder itself is never written to here;
 * 3. the app's converted copy, `converted/<SET>/<ID>_small.jpg`.
 * Decoded images (and misses) are cached in memory; the cache is capped because decoding every
 * card would hold roughly 220 MB.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FileCardThumbnails(
    private val convertedRoot: Path,
    private val generatedRoot: Path,
) : CardThumbnails {

    @Inject
    constructor(dirs: AppDirectories) : this(dirs.converted, dirs.cache.resolve("thumbnails"))

    private val cache = object : LinkedHashMap<String, CachedThumbnail>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedThumbnail>) = size > MAX_CACHED
    }

    override suspend fun load(cardsPath: String, card: Card): ImageBitmap? {
        val key = "$cardsPath|${card.id}"
        synchronized(cache) { cache[key] }?.let { return it.image }

        val image = withContext(Dispatchers.IO) {
            val simDir = Path(cardsPath).resolve(card.setId)
            val simSmall = simDir.resolve("${card.id}_small.jpg")
            val generated = if (simSmall.isRegularFile()) null else generatedFromFullImage(simDir, card)
            listOfNotNull(simSmall, generated, convertedRoot.resolve(card.setId).resolve("${card.id}_small.jpg"))
                .filter { it.isRegularFile() }
                .firstNotNullOfOrNull(::decode)
        }
        synchronized(cache) { cache[key] = CachedThumbnail(image) }
        return image
    }

    override fun invalidate(cardId: String) {
        synchronized(cache) { cache.keys.removeAll { it.endsWith("|$cardId") } }
    }

    /**
     * A thumbnail made from the sim's full image, as the sim would pick it (`.jpg` over `.png`).
     * Reuses the saved one unless the full image is newer. Null if there's no usable full image.
     */
    private fun generatedFromFullImage(simDir: Path, card: Card): Path? {
        val full = listOf(simDir.resolve("${card.id}.jpg"), simDir.resolve("${card.id}.png"))
            .firstOrNull { it.isRegularFile() } ?: return null
        val target = generatedRoot.resolve(card.setId).resolve("${card.id}_small.jpg")
        if (target.isRegularFile() && target.getLastModifiedTime() >= full.getLastModifiedTime()) return target
        val bytes = try {
            SimCardImages.renderSmall(full.readBytes())
        } catch (_: Exception) {
            return null // Unreadable or corrupt full image: fall through to the next source.
        }
        target.parent.createDirectories()
        val temp = Files.createTempFile(target.parent, ".${target.name}", ".tmp")
        try {
            Files.write(temp, bytes)
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
        return target
    }

    /** Null for unreadable or corrupt files, so the UI shows the placeholder instead of failing. */
    private fun decode(file: Path): ImageBitmap? = try {
        Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }

    private class CachedThumbnail(val image: ImageBitmap?)

    private companion object {
        const val MAX_CACHED = 400
    }
}
