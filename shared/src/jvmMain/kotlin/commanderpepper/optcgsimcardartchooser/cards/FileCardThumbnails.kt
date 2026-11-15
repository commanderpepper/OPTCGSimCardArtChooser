package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import commanderpepper.optcgsimcardartchooser.catalog.conversion.AppDirectories
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes

/**
 * Thumbnails from disk, in order of preference:
 * 1. the sim's own `<Cards>/<SET>/<ID>_small.jpg` — the art the sim currently shows;
 * 2. the app's converted copy, `converted/<SET>/<ID>_small.jpg`.
 * Decoded images (and misses) are cached; the cache is capped because decoding every card
 * would hold roughly 220 MB.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FileCardThumbnails(private val convertedRoot: Path) : CardThumbnails {

    @Inject
    constructor(dirs: AppDirectories) : this(dirs.converted)

    private val cache = object : LinkedHashMap<String, CachedThumbnail>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedThumbnail>) = size > MAX_CACHED
    }

    override suspend fun load(cardsPath: String, card: Card): ImageBitmap? {
        val key = "$cardsPath|${card.id}"
        synchronized(cache) { cache[key] }?.let { return it.image }

        val image = withContext(Dispatchers.IO) {
            candidates(cardsPath, card).firstOrNull { it.isRegularFile() }?.let(::decode)
        }
        synchronized(cache) { cache[key] = CachedThumbnail(image) }
        return image
    }

    override fun invalidate(cardId: String) {
        synchronized(cache) { cache.keys.removeAll { it.endsWith("|$cardId") } }
    }

    private fun candidates(cardsPath: String, card: Card): List<Path> = listOf(
        Path(cardsPath).resolve(card.setId).resolve("${card.id}_small.jpg"),
        convertedRoot.resolve(card.setId).resolve("${card.id}_small.jpg"),
    )

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
