package commanderpepper.optcgsimcardartchooser.catalog.image

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.max
import kotlin.math.roundToInt

/** The two files OPTCGSim reads per card: `<ID>.png` and `<ID>_small.jpg`. */
class SimImageSet(val fullPng: ByteArray, val smallJpg: ByteArray)

/**
 * Converts any card image into the formats of the sim's stock files:
 * - `<ID>.png`: 480x671, 8-bit palette (256 colors), 300 DPI.
 * - `<ID>_small.jpg`: 120x167, 24-bit RGB, JFIF with no physical DPI (units 0, ratio 1:1),
 *   which Windows reports as 96 DPI.
 *
 * Sources with a slightly different aspect ratio are scaled to cover the target and
 * center-cropped rather than stretched. The sim itself is lenient (stock files include odd
 * sizes and even JPEG data named .png), so these specs are for consistency, not compatibility.
 */
object SimCardImages {
    const val FULL_WIDTH = 480
    const val FULL_HEIGHT = 671
    const val FULL_DPI = 300
    const val SMALL_WIDTH = 120
    const val SMALL_HEIGHT = 167
    private const val JPEG_QUALITY = 0.9f
    private const val PNG_FORMAT = "javax_imageio_png_1.0"
    private const val JPEG_FORMAT = "javax_imageio_jpeg_image_1.0"

    fun render(source: ByteArray, dither: Boolean = true): SimImageSet {
        val image = decode(source)
        val full = resizeToCover(image, FULL_WIDTH, FULL_HEIGHT)
        val small = resizeToCover(image, SMALL_WIDTH, SMALL_HEIGHT)
        return SimImageSet(
            fullPng = writePalettePng(PaletteQuantizer.quantize(full, 256, dither), FULL_DPI),
            smallJpg = writeJpeg(small),
        )
    }

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

    /** Writes an indexed image as an 8-bit palette PNG with a pHYs chunk for [dpi]. */
    private fun writePalettePng(indexed: BufferedImage, dpi: Int): ByteArray = write("png") { writer ->
        val param = writer.defaultWriteParam
        val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(indexed), param)
        // Native PNG tree with an exact integer: the generic format's mm-per-pixel route drifts.
        val pixelsPerMeter = (dpi / 0.0254).roundToInt().toString()
        val root = IIOMetadataNode(PNG_FORMAT).apply {
            appendChild(IIOMetadataNode("pHYs").apply {
                setAttribute("pixelsPerUnitXAxis", pixelsPerMeter)
                setAttribute("pixelsPerUnitYAxis", pixelsPerMeter)
                setAttribute("unitSpecifier", "meter")
            })
        }
        metadata.mergeTree(PNG_FORMAT, root)
        IIOImage(indexed, null, metadata) to param
    }

    /** Writes a 24-bit baseline JPEG whose JFIF header matches the stock files (units 0, 1:1). */
    private fun writeJpeg(rgb: BufferedImage): ByteArray = write("jpeg") { writer ->
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = JPEG_QUALITY
        }
        val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(rgb), param)
        val tree = metadata.getAsTree(JPEG_FORMAT) as IIOMetadataNode
        (tree.getElementsByTagName("app0JFIF").item(0) as? IIOMetadataNode)?.apply {
            setAttribute("resUnits", "0")
            setAttribute("Xdensity", "1")
            setAttribute("Ydensity", "1")
        }
        metadata.setFromTree(JPEG_FORMAT, tree)
        IIOImage(rgb, null, metadata) to param
    }

    private fun write(format: String, prepare: (ImageWriter) -> Pair<IIOImage, ImageWriteParam>): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName(format).next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val (image, param) = prepare(writer)
                writer.write(null, image, param)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }
}
