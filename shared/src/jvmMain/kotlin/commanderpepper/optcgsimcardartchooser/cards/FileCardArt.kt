package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import commanderpepper.optcgsimcardartchooser.catalog.CatalogJson
import commanderpepper.optcgsimcardartchooser.catalog.conversion.AppDirectories
import commanderpepper.optcgsimcardartchooser.catalog.conversion.SetConverter
import commanderpepper.optcgsimcardartchooser.catalog.conversion.VariantResult
import commanderpepper.optcgsimcardartchooser.catalog.image.SimCardImages
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Swaps card art on disk. For card `OP01-001` in a sim whose cards live at `<Cards>`:
 * - converted art: `<data>/converted/OP01/<variantId>.jpg` + `_small.jpg` (made on demand);
 * - the user's own images: `<data>/custom/OP01/OP01-001_custom.jpg`, `OP01-001_custom2.jpg`, … each with `_small.jpg`;
 * - applied art: written to `<Cards>/OP01/OP01-001.jpg` + `OP01-001_small.jpg`. The sim prefers
 *   `<ID>.jpg` over `<ID>.png`, so this wins whichever format the stock art uses; a stock
 *   `<ID>.png` is left untouched;
 * - originals: the stock `.png`, `.jpg` and `_small.jpg` that exist when a card is first changed
 *   are copied to `<data>/backups/OP01/`, never overwritten;
 * - choices: `<data>/selections.json` (card ID → variant ID).
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FileCardArt(
    private val dirs: AppDirectories,
    private val converter: SetConverter,
    /** For images dropped from a browser, which arrive as web addresses. */
    private val http: HttpClient,
) : CardArt {

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 25_000_000
    }

    private val selectionsFile: Path get() = dirs.data.resolve("selections.json")
    private val mutex = Mutex()

    override suspend fun variantPreview(card: Card, variant: Variant): ImageBitmap? {
        val (full, _) = try {
            artFiles(card, variant)
        } catch (_: IllegalStateException) {
            return null
        }
        return decode(full)
    }

    /**
     * The sim-ready (full, small) files for [variant]: the user's custom image, or catalog art
     * converted on demand. Throws with a readable message if they can't be produced.
     */
    private suspend fun artFiles(card: Card, variant: Variant): Pair<Path, Path> {
        if (CustomArt.isCustom(variant)) {
            migrateLegacyCustomImage(card)
            val files = customFiles(card, variant.id)
            if (!files.first.isRegularFile() || !files.second.isRegularFile()) {
                error("the custom image is missing; choose the image again")
            }
            return files
        }
        val result = converter.convertVariant(card, variant)
        if (result.status == VariantResult.Status.Failed) {
            error("the art couldn't be downloaded or converted (${result.message})")
        }
        return converter.convertedFiles(card.setId, variant.id)
    }

    private fun customDir(card: Card): Path = dirs.data.resolve("custom").resolve(card.setId)

    /** `custom/<SET>/<variantId>.jpg` and `<variantId>_small.jpg`. */
    private fun customFiles(card: Card, variantId: String): Pair<Path, Path> =
        customDir(card).let { it.resolve("$variantId.jpg") to it.resolve("${variantId}_small.jpg") }

    /** The first version stored one image per card as `custom/<SET>/<cardId>.jpg`; rename it. */
    private fun migrateLegacyCustomImage(card: Card) {
        val dir = customDir(card)
        val legacy = dir.resolve("${card.id}.jpg") to dir.resolve("${card.id}_small.jpg")
        val current = customFiles(card, CustomArt.variantId(card.id))
        if (legacy.first.isRegularFile() && !current.first.exists()) {
            Files.move(legacy.first, current.first)
            if (legacy.second.isRegularFile()) Files.move(legacy.second, current.second)
        }
    }

    private fun customVariantIds(card: Card): List<String> {
        migrateLegacyCustomImage(card)
        val dir = customDir(card)
        if (!dir.isDirectory()) return emptyList()
        return dir.listDirectoryEntries("${card.id}_custom*.jpg")
            .map { it.name.removeSuffix(".jpg") }
            .filter { CustomArt.isCustomId(it) && CustomArt.resolve(card, it) != null }
            .sortedBy(CustomArt::number)
    }

    override suspend fun customImageCounts(): Map<String, Int> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val root = dirs.data.resolve("custom")
            if (!root.isDirectory()) return@withContext emptyMap()
            root.listDirectoryEntries().filter { it.isDirectory() }
                .flatMap { it.listDirectoryEntries("*.jpg") }
                .map { it.name.removeSuffix(".jpg") }
                .filterNot { it.endsWith("_small") }
                // Not yet migrated single-image files are named after the card itself.
                .groupingBy { CustomArt.cardId(it) ?: it }
                .eachCount()
        }
    }

    override suspend fun customImages(card: Card): List<CustomImage> = mutex.withLock {
        withContext(Dispatchers.IO) {
            customVariantIds(card).map { id -> CustomImage(CustomArt.variant(id), decode(customFiles(card, id).first)) }
        }
    }

    override suspend fun addCustomImage(card: Card, sourcePath: String): Pair<CustomImage, CustomImageInfo> {
        val bytes = readImageSource(sourcePath) // may download: outside the lock
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val next = (customVariantIds(card).maxOfOrNull(CustomArt::number) ?: 0) + 1
                storeCustomImage(card, CustomArt.variantId(card.id, next), bytes)
            }
        }
    }

    override suspend fun replaceCustomImage(card: Card, variantId: String, sourcePath: String): Pair<CustomImage, CustomImageInfo> {
        require(CustomArt.resolve(card, variantId)?.let(CustomArt::isCustom) == true) { "$variantId isn't a custom image of ${card.id}" }
        val bytes = readImageSource(sourcePath)
        return mutex.withLock { withContext(Dispatchers.IO) { storeCustomImage(card, variantId, bytes) } }
    }

    /**
     * The image bytes behind [source]: a local JPG, PNG or WebP file, an image dropped from a browser
     * (`http(s):` address, downloaded), or an inline `data:` image.
     */
    private suspend fun readImageSource(source: String): ByteArray = when {
        CustomArt.isRemote(source) && source.startsWith("data:", ignoreCase = true) -> decodeDataUri(source)
        CustomArt.isRemote(source) -> download(source)
        else -> withContext(Dispatchers.IO) {
            if (!CustomArt.isAcceptedFile(source)) error("only JPG, PNG and WebP images are supported")
            try {
                Path(source).readBytes()
            } catch (e: Exception) {
                error("the file couldn't be read (${e.message})")
            }
        }
    }

    private suspend fun download(url: String): ByteArray {
        val response = try {
            http.get(url)
        } catch (e: Exception) {
            error("the image couldn't be downloaded (${e.message})")
        }
        if (!response.status.isSuccess()) error("the image couldn't be downloaded (HTTP ${response.status.value})")
        val type = response.contentType()?.withoutParameters()?.toString().orEmpty()
        if (type.startsWith("text/")) error("that link is a web page, not an image")
        val length = response.contentLength()
        if (length != null && length > MAX_DOWNLOAD_BYTES) error("the image is larger than ${MAX_DOWNLOAD_BYTES / 1_000_000} MB")
        val bytes = response.readRawBytes()
        if (bytes.size > MAX_DOWNLOAD_BYTES) error("the image is larger than ${MAX_DOWNLOAD_BYTES / 1_000_000} MB")
        return bytes
    }

    private fun decodeDataUri(uri: String): ByteArray {
        val (header, data) = uri.substringAfter(':').split(',', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        if (!header.endsWith(";base64", ignoreCase = true)) error("that dropped image couldn't be read")
        return try {
            Base64.getDecoder().decode(data.trim())
        } catch (_: IllegalArgumentException) {
            error("that dropped image couldn't be read")
        }
    }

    /** Converts the image [source] bytes (JPG, PNG or WebP) into the sim's formats and saves them as [variantId]. */
    private fun storeCustomImage(card: Card, variantId: String, source: ByteArray): Pair<CustomImage, CustomImageInfo> {
        val bytes = WebpSupport.toConverterReadable(source)
        val decoded = try {
            SimCardImages.decode(bytes)
        } catch (_: IllegalArgumentException) {
            error("that file isn't a readable JPG, PNG or WebP image")
        }
        val images = SimCardImages.render(bytes)
        val (full, small) = customFiles(card, variantId)
        full.parent.createDirectories()
        writeAtomically(full, images.fullJpg)
        writeAtomically(small, images.smallJpg)
        return CustomImage(CustomArt.variant(variantId), decodeNow(full)) to CustomImageInfo(decoded.width, decoded.height)
    }

    override suspend fun removeCustomImage(card: Card, variantId: String) = mutex.withLock {
        withContext(Dispatchers.IO) { customFiles(card, variantId).toList().forEach { it.deleteIfExists() } }
    }

    private fun writeAtomically(target: Path, bytes: ByteArray) {
        val temp = Files.createTempFile(target.parent, ".${target.name}", ".tmp")
        try {
            Files.write(temp, bytes)
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override suspend fun originalPreview(cardsPath: String, card: Card): ImageBitmap? {
        val sim = simFiles(cardsPath, card)
        val backup = backupFiles(card)
        val changed = mutex.withLock { card.id in readSelections() }
        // Same preference as the sim: .jpg before .png. Once changed, the sim's .jpg is ours.
        val candidates = listOf(backup.jpg, backup.png) + if (changed) listOf(sim.png) else listOf(sim.jpg, sim.png)
        return candidates.firstOrNull { it.isRegularFile() }?.let { decode(it) }
    }

    override suspend fun apply(cardsPath: String, card: Card, variant: Variant) = mutex.withLock {
        val (convertedFull, convertedSmall) = artFiles(card, variant)
        val sim = simFiles(cardsPath, card)
        val backup = backupFiles(card)
        val firstChange = card.id !in readSelections()
        withContext(Dispatchers.IO) {
            sim.jpg.parent.createDirectories()
            backup.jpg.parent.createDirectories()
            // Keep the stock files before the first change. Never back up later: by then the
            // sim's .jpg and _small.jpg are our own art. Existing backups are never overwritten.
            if (firstChange) {
                for ((simFile, backupFile) in sim.all.zip(backup.all)) {
                    if (!backupFile.exists() && simFile.isRegularFile()) Files.copy(simFile, backupFile)
                }
            }
            Files.copy(convertedFull, sim.jpg, StandardCopyOption.REPLACE_EXISTING)
            Files.copy(convertedSmall, sim.small, StandardCopyOption.REPLACE_EXISTING)
        }
        writeSelections(readSelections() + (card.id to variant.id))
    }

    override suspend fun restoreOriginal(cardsPath: String, card: Card) = mutex.withLock {
        val sim = simFiles(cardsPath, card)
        val backup = backupFiles(card)
        val changed = card.id in readSelections()
        withContext(Dispatchers.IO) {
            for ((simFile, backupFile) in sim.all.zip(backup.all)) {
                when {
                    backupFile.isRegularFile() -> Files.copy(backupFile, simFile, StandardCopyOption.REPLACE_EXISTING)
                    // No backup of a file we write means the sim didn't have it: remove ours.
                    changed && simFile != sim.png -> simFile.deleteIfExists()
                }
            }
        }
        writeSelections(readSelections() - card.id)
    }

    override suspend fun appliedVariant(cardId: String): String? = mutex.withLock { readSelections()[cardId] }

    override suspend fun appliedVariants(): Map<String, String> = mutex.withLock { withContext(Dispatchers.IO) { readSelections() } }

    override suspend fun reapply(cardsPath: String, card: Card, variant: Variant): ReapplyOutcome = mutex.withLock {
        val (convertedFull, convertedSmall) = artFiles(card, variant)
        val sim = simFiles(cardsPath, card)
        val backup = backupFiles(card)
        withContext(Dispatchers.IO) {
            if (sameBytes(sim.jpg, convertedFull) && sameBytes(sim.small, convertedSmall)) {
                return@withContext ReapplyOutcome.AlreadyApplied
            }
            sim.jpg.parent.createDirectories()
            backup.jpg.parent.createDirectories()
            // Whatever the sim put in place of our art is its current original: keep that as the backup,
            // so restoring later doesn't bring back art from before the update.
            for ((simFile, backupFile, ours) in listOf(
                Triple(sim.jpg, backup.jpg, convertedFull),
                Triple(sim.small, backup.small, convertedSmall),
            )) {
                if (simFile.isRegularFile() && !sameBytes(simFile, ours)) {
                    Files.copy(simFile, backupFile, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            // The stock .png is never ours; refresh its backup if the update changed it.
            if (sim.png.isRegularFile() && !sameBytes(sim.png, backup.png)) {
                Files.copy(sim.png, backup.png, StandardCopyOption.REPLACE_EXISTING)
            }
            Files.copy(convertedFull, sim.jpg, StandardCopyOption.REPLACE_EXISTING)
            Files.copy(convertedSmall, sim.small, StandardCopyOption.REPLACE_EXISTING)
            ReapplyOutcome.Reapplied
        }
    }

    private fun sameBytes(a: Path, b: Path): Boolean =
        a.isRegularFile() && b.isRegularFile() && Files.mismatch(a, b) == -1L

    /** A card's three possible image files in one folder. */
    private class CardFiles(dir: Path, cardId: String) {
        val png: Path = dir.resolve("$cardId.png")
        val jpg: Path = dir.resolve("$cardId.jpg")
        val small: Path = dir.resolve("${cardId}_small.jpg")
        val all = listOf(png, jpg, small)
    }

    private fun simFiles(cardsPath: String, card: Card) = CardFiles(Path(cardsPath).resolve(card.setId), card.id)

    private fun backupFiles(card: Card) = CardFiles(dirs.backups.resolve(card.setId), card.id)

    private suspend fun decode(file: Path): ImageBitmap? = withContext(Dispatchers.IO) { decodeNow(file) }

    /** Null for missing, unreadable or corrupt files. Blocking: call off the UI thread. */
    private fun decodeNow(file: Path): ImageBitmap? = try {
        if (file.isRegularFile()) Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap() else null
    } catch (_: Exception) {
        null
    }

    private val selectionsSerializer = MapSerializer(String.serializer(), String.serializer())

    private fun readSelections(): Map<String, String> = try {
        if (selectionsFile.isRegularFile()) CatalogJson.json.decodeFromString(selectionsSerializer, selectionsFile.readText()) else emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }

    private fun writeSelections(selections: Map<String, String>) {
        selectionsFile.parent.createDirectories()
        val temp = Files.createTempFile(selectionsFile.parent, ".selections", ".tmp")
        temp.writeText(CatalogJson.json.encodeToString(selectionsSerializer, selections.toSortedMap()))
        Files.move(temp, selectionsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
