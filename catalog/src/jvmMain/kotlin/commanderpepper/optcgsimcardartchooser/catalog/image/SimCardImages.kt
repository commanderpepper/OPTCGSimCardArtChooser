package commanderpepper.optcgsimcardartchooser.catalog.image

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.max
import kotlin.math.roundToInt

/** The two files OPTCGSim reads per card: `<ID>.jpg` and `<ID>_small.jpg`. */
class SimImageSet(val fullJpg: ByteArray, val smallJpg: ByteArray)

/**
 * Converts any card image into the formats of the sim's current stock files:
 * - `<ID>.jpg`: 480x671, 24-bit baseline JPEG, 300 DPI (like every set since OP09 and all patches);
 * - `<ID>_small.jpg`: 120x167, 24-bit baseline JPEG, JFIF with no physical DPI (units 0, ratio 1:1).
 *
 * JPEG rather than PNG because when a card has both `<ID>.jpg` and `<ID>.png`, the sim shows the
 * JPEG (tested by the user), so a JPEG reliably replaces either stock format.
 * Sources with a slightly different aspect ratio are scaled to cover and center-cropped.
 */
object SimCardImages {
    const val FULL_WIDTH = 480
    const val FULL_HEIGHT = 671
    const val FULL_DPI = 300
    const val SMALL_WIDTH = 120
    const val SMALL_HEIGHT = 167
    private const val JPEG_QUALITY = 0.9f
    private const val JPEG_FORMAT = "javax_imageio_jpeg_image_1.0"

    fun render(source: ByteArray): SimImageSet {
        val image = decode(source)
        return SimImageSet(
            fullJpg = writeJpeg(resizeToCover(image, FULL_WIDTH, FULL_HEIGHT), dpi = FULL_DPI),
            smallJpg = writeJpeg(resizeToCover(image, SMALL_WIDTH, SMALL_HEIGHT), dpi = null),
        )
    }

    /** Just the `<ID>_small.jpg` thumbnail, e.g. for a card whose sim folder lacks one. */
    fun renderSmall(source: ByteArray): ByteArray =
        writeJpeg(resizeToCover(decode(source), SMALL_WIDTH, SMALL_HEIGHT), dpi = null)

    fun decode(bytes: ByteArray): BufferedImage =
        ImageIO.read(bytes.inputStream()) ?: throw IllegalArgumentException("Unsupported or corrupt image data")

    /** Scales to cover [width]x[height], center-crops, and flattens onto white (cards are opaque). */
    fun resizeToCover(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val scale = max(width.toDouble() / source.width, height.toDouble() / source.height)
        val scaledWidth = (source.width * scale).roundToInt()
        val scaledHeight = (source.height * scale).roundToInt()

        // Halve in steps first: one big bicubic jump (e.g. 600 → 120 px) aliases badly.
        var current = source
        while (current.width / 2 >= scaledWidth && current.height / 2 >= scaledHeight) {
            current = draw(current, current.width / 2, current.height / 2, 0, 0, current.width / 2, current.height / 2)
        }
        return draw(current, width, height, (width - scaledWidth) / 2, (height - scaledHeight) / 2, scaledWidth, scaledHeight)
    }

    private fun draw(source: BufferedImage, width: Int, height: Int, x: Int, y: Int, drawWidth: Int, drawHeight: Int): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.drawImage(source, x, y, drawWidth, drawHeight, null)
        } finally {
            g.dispose()
        }
        return target
    }

    /**
     * Writes a 24-bit baseline JPEG. With [dpi], the JFIF header records it in dots per inch;
     * without, it carries no physical size (units 0, ratio 1:1), as in the stock thumbnails.
     */
    private fun writeJpeg(rgb: BufferedImage, dpi: Int?): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = JPEG_QUALITY
                }
                val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(rgb), param)
                val tree = metadata.getAsTree(JPEG_FORMAT) as IIOMetadataNode
                (tree.getElementsByTagName("app0JFIF").item(0) as? IIOMetadataNode)?.apply {
                    setAttribute("resUnits", if (dpi != null) "1" else "0")
                    setAttribute("Xdensity", (dpi ?: 1).toString())
                    setAttribute("Ydensity", (dpi ?: 1).toString())
                }
                metadata.setFromTree(JPEG_FORMAT, tree)
                writer.write(null, IIOImage(rgb, null, metadata), param)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }
}
