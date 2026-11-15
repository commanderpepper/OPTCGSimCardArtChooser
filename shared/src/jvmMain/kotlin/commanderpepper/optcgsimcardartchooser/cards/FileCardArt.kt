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
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Swaps card art on disk. For card `OP01-001` in a sim whose cards live at `<Cards>`:
 * - converted art: `<data>/converted/OP01/<variantId>.jpg` + `_small.jpg` (made on demand);
 * - the user's own image: `<data>/custom/OP01/OP01-001.jpg` + `_small.jpg` (one per card);
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
) : CardArt {

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
            val files = customFiles(card)
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

    private fun customFiles(card: Card): Pair<Path, Path> =
        dirs.data.resolve("custom").resolve(card.setId).let { it.resolve("${card.id}.jpg") to it.resolve("${card.id}_small.jpg") }

    override suspend fun setCustomImage(card: Card, sourcePath: String): CustomImageInfo = mutex.withLock {
        val source = Path(sourcePath)
        val extension = source.extension.lowercase()
        if (extension !in CustomArt.ACCEPTED_EXTENSIONS) error("only JPG and PNG images are supported")
        withContext(Dispatchers.IO) {
            val bytes = try {
                source.readBytes()
            } catch (e: Exception) {
                error("the file couldn't be read (${e.message})")
            }
            val decoded = try {
                SimCardImages.decode(bytes)
            } catch (_: IllegalArgumentException) {
                error("that file isn't a readable JPG or PNG image")
            }
            val images = SimCardImages.render(bytes)
            val (full, small) = customFiles(card)
            full.parent.createDirectories()
            writeAtomically(full, images.fullJpg)
            writeAtomically(small, images.smallJpg)
            CustomImageInfo(decoded.width, decoded.height)
        }
    }

    override suspend fun customPreview(card: Card): ImageBitmap? = decode(customFiles(card).first)

    override suspend fun removeCustomImage(card: Card) = mutex.withLock {
        withContext(Dispatchers.IO) { customFiles(card).toList().forEach { it.deleteIfExists() } }
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

    private suspend fun decode(file: Path): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            if (file.isRegularFile()) Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap() else null
        } catch (_: Exception) {
            null
        }
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
