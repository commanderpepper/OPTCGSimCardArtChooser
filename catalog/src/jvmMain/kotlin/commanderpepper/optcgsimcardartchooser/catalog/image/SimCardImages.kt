package commanderpepper.optcgsimcardartchooser.catalog.image

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/** The two files OPTCGSim reads per card: `<ID>.png` and `<ID>_small.jpg`. */
class SimImageSet(val fullPng: ByteArray, val smallJpg: ByteArray)

/**
 * Converts any card image into the formats OPTCGSim uses. Sizes match the sim's own files
 * (480x671 PNG, 120x167 JPEG); sources with a slightly different aspect ratio are scaled to
 * cover the target and center-cropped rather than stretched.
 */
object SimCardImages {
    const val FULL_WIDTH = 480
    const val FULL_HEIGHT = 671
    const val SMALL_WIDTH = 120
    const val SMALL_HEIGHT = 167
    private const val JPEG_QUALITY = 0.9f

    fun render(source: ByteArray): SimImageSet {
        val image = decode(source)
        return SimImageSet(
            fullPng = writePng(resizeToCover(image, FULL_WIDTH, FULL_HEIGHT, BufferedImage.TYPE_INT_ARGB)),
            smallJpg = writeJpeg(resizeToCover(image, SMALL_WIDTH, SMALL_HEIGHT, BufferedImage.TYPE_INT_RGB)),
        )
    }

    fun decode(bytes: ByteArray): BufferedImage =
        ImageIO.read(bytes.inputStream()) ?: throw IllegalArgumentException("Unsupported or corrupt image data")

    fun resizeToCover(source: BufferedImage, width: Int, height: Int, type: Int): BufferedImage {
        val scale = max(width.toDouble() / source.width, height.toDouble() / source.height)
        val scaledWidth = (source.width * scale).roundToInt()
        val scaledHeight = (source.height * scale).roundToInt()

        // Halve in steps first: one big bicubic jump (e.g. 600 → 120 px) aliases badly.
        var current = source
        while (current.width / 2 >= scaledWidth && current.height / 2 >= scaledHeight) {
            current = draw(current, current.width / 2, current.height / 2, BufferedImage.TYPE_INT_ARGB, 0, 0, current.width / 2, current.height / 2)
        }
        return draw(
            current, width, height, type,
            x = (width - scaledWidth) / 2, y = (height - scaledHeight) / 2,
            drawWidth = scaledWidth, drawHeight = scaledHeight,
        )
    }

    private fun draw(
        source: BufferedImage, width: Int, height: Int, type: Int,
        x: Int, y: Int, drawWidth: Int, drawHeight: Int,
    ): BufferedImage {
        val target = BufferedImage(width, height, type)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            if (type == BufferedImage.TYPE_INT_RGB) {
                g.color = Color.WHITE // JPEG has no alpha; flatten transparency onto white.
                g.fillRect(0, 0, width, height)
            }
            g.drawImage(source, x, y, drawWidth, drawHeight, null)
        } finally {
            g.dispose()
        }
        return target
    }

    private fun writePng(image: BufferedImage): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()

    private fun writeJpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val params = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = JPEG_QUALITY
                }
                writer.write(null, IIOImage(image, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }
}
