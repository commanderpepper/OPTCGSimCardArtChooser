package commanderpepper.optcgsimcardartchooser.catalog.image

import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaletteQuantizerTest {

    private fun image(width: Int, height: Int, color: (Int, Int) -> Int) =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, color(x, y))
        }

    @Test
    fun imagesWithFewColorsAreReproducedExactly() {
        val colors = intArrayOf(0xFF0000, 0x00FF00, 0x0000FF, 0x123456)
        val source = image(40, 40) { x, y -> colors[(x / 10 + y / 10) % colors.size] }
        val result = PaletteQuantizer.quantize(source, dither = false)
        for (y in 0 until 40) for (x in 0 until 40) assertEquals(source.getRGB(x, y), result.getRGB(x, y), "pixel $x,$y")
    }

    @Test
    fun manyColorsAreReducedToAtMost256WithAnEightBitModel() {
        val source = image(256, 256) { x, y -> (x shl 16) or (y shl 8) or ((x + y) / 2) }
        val result = PaletteQuantizer.quantize(source)
        val model = result.colorModel as IndexColorModel
        assertEquals(BufferedImage.TYPE_BYTE_INDEXED, result.type)
        assertEquals(8, model.pixelSize)
        assertTrue(model.mapSize in 200..256, "palette size ${model.mapSize}")
    }

    @Test
    fun medianCutHonorsTheColorLimit() {
        val pixels = IntArray(10_000) { it * 1677 and 0xFFFFFF }
        assertEquals(16, PaletteQuantizer.medianCut(pixels, 16).size)
    }
}
