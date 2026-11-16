package commanderpepper.optcgsimcardartchooser.catalog.conversion

import commanderpepper.optcgsimcardartchooser.catalog.image.SimCardImages
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes

data class VariantResult(val cardId: String, val variantId: String, val status: Status, val message: String? = null) {
    enum class Status { Converted, AlreadyConverted, Failed }
}

/**
 * Downloads a variant's art from the catalog's image URLs and converts it into sim-ready files:
 * `converted/<SET>/<variantId>.jpg` and `<variantId>_small.jpg`.
 * Raw downloads are cached in `downloads/`; existing conversions and downloads are reused, so
 * only what's missing is fetched.
 */
class SetConverter(
    private val http: HttpClient,
    private val dirs: AppDirectories,
    /** Pause between network requests to go easy on the image host. */
    private val requestDelayMillis: Long = 300,
) {
    /** Converts a single variant if it isn't already (downloading it if needed). */
    suspend fun convertVariant(card: Card, variant: Variant): VariantResult {
        val outDir = dirs.convertedSet(card.setId).createDirectories()
        dirs.downloads.createDirectories()
        return convertOne(card, variant, outDir)
    }

    /** Where [convertVariant] puts a variant's sim-ready files: (full JPEG, small JPEG). */
    fun convertedFiles(setId: String, variantId: String): Pair<Path, Path> =
        dirs.convertedSet(setId).let { it.resolve("$variantId.jpg") to it.resolve("${variantId}_small.jpg") }

    private suspend fun convertOne(card: Card, variant: Variant, outDir: Path): VariantResult {
        val full = outDir.resolve("${variant.id}.jpg")
        val small = outDir.resolve("${variant.id}_small.jpg")
        if (full.exists() && small.exists()) return VariantResult(card.id, variant.id, VariantResult.Status.AlreadyConverted)

        return try {
            val source = cachedDownload(variant) ?: download(variant)
            val images = withContext(Dispatchers.Default) { SimCardImages.render(source) }
            writeAtomically(full, images.fullJpg)
            writeAtomically(small, images.smallJpg)
            VariantResult(card.id, variant.id, VariantResult.Status.Converted)
        } catch (e: Exception) {
            VariantResult(card.id, variant.id, VariantResult.Status.Failed, e.message ?: e::class.simpleName)
        }
    }

    private fun cachedDownload(variant: Variant): ByteArray? =
        dirs.downloads.listDirectoryEntries("${variant.id}.*").firstOrNull()?.readBytes()

    /** Tries each known image URL in order; caches the first that returns image data. */
    private suspend fun download(variant: Variant): ByteArray {
        if (variant.images.isEmpty()) error("No image URL in the catalog")
        val failures = mutableListOf<String>()
        for (source in variant.images) {
            delay(requestDelayMillis)
            val response = try {
                http.get(source.url)
            } catch (e: Exception) {
                failures += "${source.url}: ${e.message}"; continue
            }
            if (!response.status.isSuccess()) {
                failures += "${source.url}: HTTP ${response.status.value}"; continue
            }
            val bytes = response.readRawBytes()
            val extension = imageExtension(bytes)
            if (extension == null) {
                failures += "${source.url}: not an image"; continue
            }
            writeAtomically(dirs.downloads.resolve("${variant.id}.$extension"), bytes)
            return bytes
        }
        error(failures.joinToString("; "))
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

    private companion object {
        /** By content, not URL: optcgapi serves PNG data from `.jpg` URLs. */
        fun imageExtension(bytes: ByteArray): String? = when {
            bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
            bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            bytes.size > 12 && bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "webp"
            else -> null
        }
    }
}
