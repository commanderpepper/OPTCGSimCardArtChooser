package commanderpepper.optcgsimcardartchooser.catalog.image

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SimCardImagesTest {

    private fun sourceImage(width: Int, height: Int, format: String): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, width, height)
        g.color = Color.BLUE // marks the left edge so cropping is observable
        g.fillRect(0, 0, width / 10, height)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }

    @Test
    fun rendersBothSimFormatsAtSimSizes() {
        val set = SimCardImages.render(sourceImage(600, 838, "jpg"))

        val full = ImageIO.read(set.fullPng.inputStream())
        assertEquals(SimCardImages.FULL_WIDTH to SimCardImages.FULL_HEIGHT, full.width to full.height)
        assertEquals(0x89.toByte(), set.fullPng[0]) // PNG signature

        val small = ImageIO.read(set.smallJpg.inputStream())
        assertEquals(SimCardImages.SMALL_WIDTH to SimCardImages.SMALL_HEIGHT, small.width to small.height)
        assertEquals(0xFF.toByte(), set.smallJpg[0]) // JPEG SOI
        assertEquals(0xD8.toByte(), set.smallJpg[1])
    }

    @Test
    fun wideSourceIsCenterCroppedNotStretched() {
        // Twice as wide as a card: the blue left strip (10% of width) should be cropped away.
        val full = ImageIO.read(SimCardImages.render(sourceImage(1342, 671, "png")).fullPng.inputStream())
        val leftPixel = Color(full.getRGB(2, full.height / 2))
        assertEquals(255, leftPixel.red)
        assertEquals(0, leftPixel.blue)
    }

    @Test
    fun rejectsNonImageData() {
        assertFailsWith<IllegalArgumentException> { SimCardImages.render("not an image".encodeToByteArray()) }
    }
}
