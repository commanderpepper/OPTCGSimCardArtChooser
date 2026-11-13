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
import kotlin.test.assertNotNull
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

    // --- Minimal byte-level readers, so the tests check the files themselves, not ImageIO's view.

    private class PngInfo(val width: Int, val height: Int, val bitDepth: Int, val colorType: Int, val paletteSize: Int?, val pixelsPerMeter: Pair<Int, Int>?, val unit: Int?)

    private fun readPng(bytes: ByteArray): PngInfo {
        val buf = ByteBuffer.wrap(bytes)
        buf.position(8)
        var ihdr: IntArray? = null
        var palette: Int? = null
        var phys: Pair<Int, Int>? = null
        var unit: Int? = null
        while (buf.remaining() >= 12) {
            val length = buf.int
            val type = ByteArray(4).also { buf.get(it) }.decodeToString()
            val start = buf.position()
            when (type) {
                "IHDR" -> ihdr = intArrayOf(buf.int, buf.int, buf.get().toInt(), buf.get().toInt())
                "PLTE" -> palette = length / 3
                "pHYs" -> { phys = buf.int to buf.int; unit = buf.get().toInt() }
            }
            buf.position(start + length + 4) // skip data + CRC
            if (type == "IEND") break
        }
        val h = assertNotNull(ihdr)
        return PngInfo(h[0], h[1], h[2], h[3], palette, phys, unit)
    }

    private class JpegInfo(val width: Int, val height: Int, val components: Int, val jfif: Triple<Int, Int, Int>?)

    private fun readJpeg(bytes: ByteArray): JpegInfo {
        val buf = ByteBuffer.wrap(bytes)
        assertEquals(0xFFD8, buf.short.toInt() and 0xFFFF)
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
                return JpegInfo(width, height, buf.get().toInt(), jfif)
            }
            buf.position(start + length - 2)
        }
        error("No SOF marker")
    }

    @Test
    fun fullImageMatchesSimPngSpec() {
        val png = readPng(SimCardImages.render(sourceImage(600, 838, "jpg")).fullPng)
        assertEquals(480 to 671, png.width to png.height)
        assertEquals(3, png.colorType, "palette (indexed) color")
        assertEquals(8, png.bitDepth)
        assertTrue(png.paletteSize!! in 2..256)
        assertEquals(11811 to 11811, png.pixelsPerMeter, "300 DPI")
        assertEquals(1, png.unit, "unit = meter")
        assertEquals(300, (png.pixelsPerMeter!!.first * 0.0254).let { Math.round(it).toInt() })
    }

    @Test
    fun smallImageMatchesSimJpegSpec() {
        val jpeg = readJpeg(SimCardImages.render(sourceImage(600, 838, "jpg")).smallJpg)
        assertEquals(120 to 167, jpeg.width to jpeg.height)
        assertEquals(3, jpeg.components, "24-bit RGB")
        assertEquals(Triple(0, 1, 1), jpeg.jfif, "no physical DPI, like the stock files (Windows shows 96)")
    }

    @Test
    fun paletteImageStaysCloseToTheSource() {
        val source = SimCardImages.resizeToCover(SimCardImages.decode(sourceImage(600, 838, "png")), 480, 671)
        val decoded = ImageIO.read(SimCardImages.render(sourceImage(600, 838, "png")).fullPng.inputStream())
        var totalError = 0L
        for (y in 0 until 671) for (x in 0 until 480) {
            val a = source.getRGB(x, y); val b = decoded.getRGB(x, y)
            totalError += abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))
        }
        val meanPerChannel = totalError.toDouble() / (480 * 671 * 3)
        assertTrue(meanPerChannel < 4.0, "mean channel error $meanPerChannel")
    }

    @Test
    fun wideSourceIsCenterCroppedNotStretched() {
        // Twice as wide as a card: the blue left strip (10% of width) should be cropped away.
        val full = ImageIO.read(SimCardImages.render(sourceImage(1342, 671, "png")).fullPng.inputStream())
        val leftPixel = Color(full.getRGB(2, full.height / 2))
        assertTrue(leftPixel.red > 150 && leftPixel.blue < 150, "left edge $leftPixel should not be the blue strip")
    }

    @Test
    fun transparentSourceIsFlattenedOntoWhite() {
        val transparent = BufferedImage(480, 671, BufferedImage.TYPE_INT_ARGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(transparent, "png", it) }.toByteArray()
        val fullPng = SimCardImages.render(bytes).fullPng
        assertEquals(Color.WHITE.rgb, ImageIO.read(fullPng.inputStream()).getRGB(10, 10))
        assertEquals(3 to 8, readPng(fullPng).let { it.colorType to it.bitDepth }, "still an 8-bit palette PNG")
    }

    @Test
    fun rejectsNonImageData() {
        assertFailsWith<IllegalArgumentException> { SimCardImages.render("not an image".encodeToByteArray()) }
    }
}
