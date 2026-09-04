package dev.mockarr.core.routing

import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.Inflater
import kotlin.math.abs

/** A decoded 8-bit image: [channels] bytes per pixel, row-major, no padding. */
class RgbImage(val width: Int, val height: Int, val channels: Int, val pixels: ByteArray) {
    /** Unsigned channel value at ([x], [y]). */
    fun channel(x: Int, y: Int, c: Int): Int = pixels[(y * width + x) * channels + c].toInt() and 0xFF
}

/**
 * Minimal PNG reader for the terrain tiles the elevation provider consumes:
 * 8-bit RGB or RGBA, non-interlaced, any of the five filter types. Pure
 * Kotlin/JVM (`java.util.zip`) so it runs in `core:routing` and in unit tests —
 * Android's BitmapFactory would tie the module to the framework.
 */
object Png {
    private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private const val COLOR_RGB = 2
    private const val COLOR_RGBA = 6
    private const val FILTER_NONE = 0
    private const val FILTER_SUB = 1
    private const val FILTER_UP = 2
    private const val FILTER_AVERAGE = 3
    private const val FILTER_PAETH = 4
    private const val CHUNK_OVERHEAD = 12

    @Throws(IOException::class)
    @Suppress("ThrowsCount")
    fun decode(bytes: ByteArray): RgbImage {
        if (bytes.size < SIGNATURE.size || !bytes.copyOf(SIGNATURE.size).contentEquals(SIGNATURE)) {
            throw IOException("Not a PNG")
        }
        var header: PngHeader? = null
        val idat = java.io.ByteArrayOutputStream()
        var pos = SIGNATURE.size
        while (pos + CHUNK_OVERHEAD <= bytes.size) {
            val buffer = ByteBuffer.wrap(bytes, pos, 8)
            val length = buffer.int
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val dataStart = pos + 8
            if (dataStart + length > bytes.size) throw IOException("Truncated PNG chunk $type")
            when (type) {
                "IHDR" -> header = parseIhdr(bytes, dataStart, length)
                "IDAT" -> idat.write(bytes, dataStart, length)
                "IEND" -> break
            }
            pos = dataStart + length + 4 // + CRC
        }
        val h = header
        if (h == null || !h.isValid) throw IOException("PNG without IHDR")
        val stride = h.width * h.channels
        val raw = inflate(idat.toByteArray(), (stride + 1) * h.height)
        return RgbImage(h.width, h.height, h.channels, unfilter(raw, h.width, h.height, h.channels))
    }

    private class PngHeader(val width: Int, val height: Int, val channels: Int) {
        val isValid: Boolean get() = width > 0 && height > 0 && channels != 0
    }

    private fun parseIhdr(bytes: ByteArray, dataStart: Int, length: Int): PngHeader {
        val header = ByteBuffer.wrap(bytes, dataStart, length)
        val width = header.int
        val height = header.int
        val bitDepth = header.get().toInt()
        val colorType = header.get().toInt()
        header.get() // compression
        header.get() // filter method
        val interlace = header.get().toInt()
        val channels = when (colorType) {
            COLOR_RGB -> 3
            COLOR_RGBA -> 4
            else -> throw IOException("Unsupported PNG colour type $colorType")
        }
        if (bitDepth != 8 || interlace != 0) {
            throw IOException("Unsupported PNG (depth $bitDepth, interlace $interlace)")
        }
        return PngHeader(width, height, channels)
    }

    private fun inflate(compressed: ByteArray, expected: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(compressed)
        val out = ByteArray(expected)
        var written = 0
        try {
            while (written < expected && !inflater.finished()) {
                val n = inflater.inflate(out, written, expected - written)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                written += n
            }
        } finally {
            inflater.end()
        }
        if (written < expected) throw IOException("PNG image data too short ($written < $expected)")
        return out
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    private fun unfilter(raw: ByteArray, width: Int, height: Int, bpp: Int): ByteArray {
        val stride = width * bpp
        val out = ByteArray(stride * height)
        for (y in 0 until height) {
            val filter = raw[y * (stride + 1)].toInt()
            val inRow = y * (stride + 1) + 1
            val outRow = y * stride
            val prevRow = outRow - stride
            for (i in 0 until stride) {
                val x = raw[inRow + i].toInt() and 0xFF
                val a = if (i >= bpp) out[outRow + i - bpp].toInt() and 0xFF else 0
                val b = if (y > 0) out[prevRow + i].toInt() and 0xFF else 0
                val c = if (y > 0 && i >= bpp) out[prevRow + i - bpp].toInt() and 0xFF else 0
                val predicted = when (filter) {
                    FILTER_NONE -> 0
                    FILTER_SUB -> a
                    FILTER_UP -> b
                    FILTER_AVERAGE -> (a + b) / 2
                    FILTER_PAETH -> paeth(a, b, c)
                    else -> throw IOException("Unknown PNG filter $filter")
                }
                out[outRow + i] = ((x + predicted) and 0xFF).toByte()
            }
        }
        return out
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }
}
