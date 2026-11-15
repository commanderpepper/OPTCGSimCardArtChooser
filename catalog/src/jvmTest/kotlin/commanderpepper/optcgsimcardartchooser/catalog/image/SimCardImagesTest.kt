package commanderpepper.optcgsimcardartchooser.catalog.image

import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SimCardImagesTest {

    /** A gradient card image with a blue strip on the left edge (to observe cropping). */
    private fun sourceImage(width: Int, height: Int, format: String): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.paint = GradientPaint(0f, 0f, Color(200, 40, 40), width.toFloat(), height.toFloat(), Color(250, 220, 120))
        g.fillRect(0, 0, width, height)
        g.color = Color.BLUE
        g.fillRect(0, 0, width / 10, height)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    /** What the file itself says, read from its bytes rather than through ImageIO. */
    private class JpegInfo(val width: Int, val height: Int, val components: Int, val baseline: Boolean, val jfif: Triple<Int, Int, Int>?)

    private fun readJpeg(bytes: ByteArray): JpegInfo {
        val buf = ByteBuffer.wrap(bytes)
        assertEquals(0xFFD8, buf.short.toInt() and 0xFFFF, "JPEG start-of-image marker")
        var jfif: Triple<Int, Int, Int>? = null
        while (buf.remaining() > 4) {
            assertEquals(0xFF, buf.get().toInt() and 0xFF)
            val marker = buf.get().toInt() and 0xFF
            val length = buf.short.toInt() and 0xFFFF
            val start = buf.position()
            if (marker == 0xE0) {
                val id = ByteArray(5).also { buf.get(it) }
                if (id.decodeToString() == "JFIF\u0000") {
                    buf.short // version
                    jfif = Triple(buf.get().toInt(), buf.short.toInt(), buf.short.toInt())
                }
            }
            if (marker == 0xC0 || marker == 0xC2) {
                buf.get() // precision
                val height = buf.short.toInt()
                val width = buf.short.toInt()
                return JpegInfo(width, height, buf.get().toInt(), baseline = marker == 0xC0, jfif = jfif)
            }
            buf.position(start + length - 2)
        }
        error("No SOF marker")
    }

    @Test
    fun fullImageMatchesSimJpegSpec() {
        val jpeg = readJpeg(SimCardImages.render(sourceImage(600, 838, "png")).fullJpg)
        assertEquals(480 to 671, jpeg.width to jpeg.height)
        assertEquals(3, jpeg.components, "24-bit RGB")
        assertTrue(jpeg.baseline, "baseline, like the stock files")
        assertEquals(Triple(1, 300, 300), jpeg.jfif, "300 dots per inch")
    }

    @Test
    fun smallImageMatchesSimJpegSpec() {
        val jpeg = readJpeg(SimCardImages.render(sourceImage(600, 838, "jpg")).smallJpg)
        assertEquals(120 to 167, jpeg.width to jpeg.height)
        assertEquals(3, jpeg.components, "24-bit RGB")
        assertTrue(jpeg.baseline)
        assertEquals(Triple(0, 1, 1), jpeg.jfif, "no physical DPI, like the stock files (Windows shows 96)")
    }

    @Test
    fun fullImageStaysCloseToTheSource() {
        val source = SimCardImages.resizeToCover(SimCardImages.decode(sourceImage(600, 838, "png")), 480, 671)
        val decoded = ImageIO.read(SimCardImages.render(sourceImage(600, 838, "png")).fullJpg.inputStream())
        var totalError = 0L
        for (y in 0 until 671) for (x in 0 until 480) {
            val a = source.getRGB(x, y); val b = decoded.getRGB(x, y)
            totalError += abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))
        }
        val meanPerChannel = totalError.toDouble() / (480 * 671 * 3)
        assertTrue(meanPerChannel < 3.0, "mean channel error $meanPerChannel")
    }

    @Test
    fun wideSourceIsCenterCroppedNotStretched() {
        // Twice as wide as a card: the blue left strip (10% of width) should be cropped away.
        val full = ImageIO.read(SimCardImages.render(sourceImage(1342, 671, "png")).fullJpg.inputStream())
        val leftPixel = Color(full.getRGB(2, full.height / 2))
        assertTrue(leftPixel.red > 150 && leftPixel.blue < 150, "left edge $leftPixel should not be the blue strip")
    }

    @Test
    fun transparentSourceIsFlattenedOntoWhite() {
        val transparent = BufferedImage(480, 671, BufferedImage.TYPE_INT_ARGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(transparent, "png", it) }.toByteArray()
        val pixel = Color(ImageIO.read(SimCardImages.render(bytes).fullJpg.inputStream()).getRGB(10, 10))
        assertTrue(pixel.red > 250 && pixel.green > 250 && pixel.blue > 250, "white, got $pixel")
    }

    @Test
    fun rejectsNonImageData() {
        assertFailsWith<IllegalArgumentException> { SimCardImages.render("not an image".encodeToByteArray()) }
    }
}
