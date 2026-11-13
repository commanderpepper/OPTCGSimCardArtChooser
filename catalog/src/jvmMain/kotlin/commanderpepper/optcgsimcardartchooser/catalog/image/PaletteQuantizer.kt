package commanderpepper.optcgsimcardartchooser.catalog.image

import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.IndexColorModel

/**
 * Reduces an opaque image to an 8-bit palette (at most 256 colors), as in the sim's stock PNGs.
 * Palette: median cut over a 15-bit color histogram. Mapping: nearest palette color, optionally
 * with Floyd–Steinberg dithering to avoid banding in gradients.
 * No pure-Java library does this well (JAI is abandoned, libimagequant is native), hence our own.
 */
object PaletteQuantizer {

    fun quantize(image: BufferedImage, maxColors: Int = 256, dither: Boolean = true): BufferedImage {
        require(maxColors in 2..256) { "maxColors must be 2..256" }
        val width = image.width
        val height = image.height
        val pixels = image.getRGB(0, 0, width, height, null, 0, width)

        val palette = medianCut(pixels, maxColors)
        val colorModel = IndexColorModel(
            8, palette.size,
            ByteArray(palette.size) { (palette[it] shr 16).toByte() },
            ByteArray(palette.size) { (palette[it] shr 8).toByte() },
            ByteArray(palette.size) { palette[it].toByte() },
        )
        val out = BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, colorModel)
        val indices = (out.raster.dataBuffer as DataBufferByte).data
        val mapper = NearestColor(palette)

        if (!dither) {
            for (i in pixels.indices) indices[i] = mapper.index(red(pixels[i]), green(pixels[i]), blue(pixels[i])).toByte()
            return out
        }

        // Floyd–Steinberg: carry each pixel's error to its right and lower neighbors (7/16, 3/16, 5/16, 1/16).
        var current = FloatArray((width + 2) * 3)
        var next = FloatArray((width + 2) * 3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                val e = (x + 1) * 3
                val r = clamp(red(p) + current[e])
                val g = clamp(green(p) + current[e + 1])
                val b = clamp(blue(p) + current[e + 2])
                val index = mapper.index(r, g, b)
                indices[y * width + x] = index.toByte()

                val chosen = palette[index]
                val er = (r - red(chosen)).toFloat()
                val eg = (g - green(chosen)).toFloat()
                val eb = (b - blue(chosen)).toFloat()
                spread(current, e + 3, er, eg, eb, 7f / 16)
                spread(next, e - 3, er, eg, eb, 3f / 16)
                spread(next, e, er, eg, eb, 5f / 16)
                spread(next, e + 3, er, eg, eb, 1f / 16)
            }
            current = next.also { next = current }
            next.fill(0f)
        }
        return out
    }

    /** Returns up to [maxColors] opaque RGB colors (0xRRGGBB). */
    internal fun medianCut(pixels: IntArray, maxColors: Int): IntArray {
        // Histogram on 5 bits per channel; keep exact sums so palette colors are true averages.
        val count = IntArray(HIST_SIZE)
        val sumR = LongArray(HIST_SIZE)
        val sumG = LongArray(HIST_SIZE)
        val sumB = LongArray(HIST_SIZE)
        for (p in pixels) {
            val key = key15(p)
            count[key]++
            sumR[key] += red(p).toLong()
            sumG[key] += green(p).toLong()
            sumB[key] += blue(p).toLong()
        }
        val used = (0 until HIST_SIZE).filter { count[it] > 0 }.toIntArray()

        val boxes = mutableListOf(Box(used, count))
        while (boxes.size < maxColors) {
            // Split the box with the widest channel range, weighted by how many pixels it covers.
            val target = boxes.filter { it.keys.size > 1 }.maxByOrNull { it.widestRange.toLong() * it.population } ?: break
            boxes.remove(target)
            boxes.addAll(target.split(count))
        }
        return IntArray(boxes.size) { i ->
            var n = 0L; var r = 0L; var g = 0L; var b = 0L
            for (k in boxes[i].keys) { n += count[k]; r += sumR[k]; g += sumG[k]; b += sumB[k] }
            ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }
    }

    private class Box(val keys: IntArray, count: IntArray) {
        val population: Int = keys.sumOf { count[it] }
        private val ranges = IntArray(3) { channel ->
            var min = 31; var max = 0
            for (k in keys) { val v = channel5(k, channel); if (v < min) min = v; if (v > max) max = v }
            max - min
        }
        val widestRange get() = ranges.max()

        fun split(count: IntArray): List<Box> {
            val channel = ranges.indices.maxBy { ranges[it] }
            val sorted = keys.sortedBy { channel5(it, channel) }.toIntArray()
            // Cut at the pixel-weighted median, keeping both halves non-empty.
            var running = 0
            var cut = 1
            for (i in 0 until sorted.size - 1) {
                running += count[sorted[i]]
                cut = i + 1
                if (running * 2 >= population) break
            }
            return listOf(Box(sorted.copyOfRange(0, cut), count), Box(sorted.copyOfRange(cut, sorted.size), count))
        }
    }

    /** Nearest palette entry by squared RGB distance, cached on 6 bits per channel. */
    private class NearestColor(private val palette: IntArray) {
        private val cache = IntArray(1 shl 18) { -1 }

        fun index(r: Int, g: Int, b: Int): Int {
            val key = ((r shr 2) shl 12) or ((g shr 2) shl 6) or (b shr 2)
            cache[key].let { if (it >= 0) return it }
            var best = 0
            var bestDistance = Int.MAX_VALUE
            for (i in palette.indices) {
                val dr = r - red(palette[i]); val dg = g - green(palette[i]); val db = b - blue(palette[i])
                val d = dr * dr + dg * dg + db * db
                if (d < bestDistance) { bestDistance = d; best = i }
            }
            cache[key] = best
            return best
        }
    }

    private const val HIST_SIZE = 1 shl 15
    private fun red(p: Int) = (p shr 16) and 0xFF
    private fun green(p: Int) = (p shr 8) and 0xFF
    private fun blue(p: Int) = p and 0xFF
    private fun key15(p: Int) = ((red(p) shr 3) shl 10) or ((green(p) shr 3) shl 5) or (blue(p) shr 3)
    private fun channel5(key: Int, channel: Int) = (key shr (10 - 5 * channel)) and 0x1F
    private fun clamp(v: Float) = v.toInt().coerceIn(0, 255)
    private fun spread(row: FloatArray, at: Int, er: Float, eg: Float, eb: Float, weight: Float) {
        row[at] += er * weight; row[at + 1] += eg * weight; row[at + 2] += eb * weight
    }
}
