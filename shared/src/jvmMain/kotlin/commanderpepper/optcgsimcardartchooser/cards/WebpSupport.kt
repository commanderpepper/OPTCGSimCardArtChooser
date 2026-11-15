package commanderpepper.optcgsimcardartchooser.cards

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * WebP support for custom images. The converter reads images with Java's ImageIO, which has no
 * WebP decoder, but Skia (already in the app via Compose Desktop) decodes WebP — lossy, lossless,
 * with transparency, and the first frame of animations. WebP bytes are re-encoded as PNG here;
 * anything else passes through untouched.
 */
internal object WebpSupport {
    /** RIFF container whose form type is "WEBP". */
    fun isWebp(bytes: ByteArray): Boolean =
        bytes.size > 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" && bytes.copyOfRange(8, 12).decodeToString() == "WEBP"

    /** Bytes the converter can read: PNG for WebP input, the input itself otherwise. */
    fun toConverterReadable(bytes: ByteArray): ByteArray {
        if (!isWebp(bytes)) return bytes
        val png = try {
            Image.makeFromEncoded(bytes).encodeToData(EncodedImageFormat.PNG)?.bytes
        } catch (_: Exception) {
            null
        }
        return png ?: error("that WebP image couldn't be read")
    }
}
