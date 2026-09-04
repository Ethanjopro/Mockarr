package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrariumElevationProviderTest {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `png decoder reconstructs every filter type`() {
        // 4 px wide, 5 rows, one row per filter type; pixel (x,y) = (x*40+y, 10*y, 200-x)
        // so predictions are non-trivial.
        val width = 4
        val height = 5
        val pixel = { x: Int, y: Int -> intArrayOf(x * 40 + y, 10 * y, 200 - x) }
        val png = encodePng(width, height, filters = intArrayOf(0, 1, 2, 3, 4), pixel = pixel)

        val image = Png.decode(png)

        assertEquals(3, image.channels)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val expected = pixel(x, y)
                assertEquals(expected[0], image.channel(x, y, 0), "R at $x,$y")
                assertEquals(expected[1], image.channel(x, y, 1), "G at $x,$y")
                assertEquals(expected[2], image.channel(x, y, 2), "B at $x,$y")
            }
        }
    }

    @Test
    fun `terrarium arithmetic`() {
        // R=128, G=229, B=136 → 32768 + 229 + 0.53125 − 32768 = 229.53125 m
        val image = RgbImage(1, 1, 3, byteArrayOf(128.toByte(), 229.toByte(), 136.toByte()))
        assertEquals(229.53125, TerrariumElevationProvider.elevationOf(image, 0, 0))
    }

    @Test
    fun `locate maps a tile's north-west corner to pixel 0,0 and its centre to the middle`() {
        val key = TerrariumElevationProvider.TileKey(12, 657, 1582)
        val origin = TerrariumElevationProvider.tileOrigin(key)
        val corner = TerrariumElevationProvider.locate(LatLng(origin.latitude - 1e-9, origin.longitude + 1e-9), 12)
        assertEquals(key, corner.tile)
        assertEquals(0, corner.px)
        assertEquals(0, corner.py)
    }

    @Test
    fun `fetches each tile once and reads elevations per pixel`() = runTest {
        // Whole tile at a constant 229.53125 m (R=128,G=229,B=136).
        val png = encodePng(256, 256, filters = IntArray(256) { 0 }) { _, _ -> intArrayOf(128, 229, 136) }
        server.enqueue(MockResponse().setBody(Buffer().write(png)))
        val provider = TerrariumElevationProvider(
            userAgent = "MockarrTest/0.0",
            baseUrl = server.url("/tiles").toString(),
        )
        val origin = TerrariumElevationProvider.tileOrigin(TerrariumElevationProvider.TileKey(12, 657, 1582))
        val inside = listOf(
            LatLng(origin.latitude - 0.01, origin.longitude + 0.01),
            LatLng(origin.latitude - 0.02, origin.longitude + 0.03),
        )

        val elevations = provider.elevations(inside).getOrThrow()

        assertEquals(listOf(229.53125, 229.53125), elevations)
        assertEquals(1, server.requestCount)
        assertEquals("/tiles/12/657/1582.png", server.takeRequest().path)
    }

    @Test
    fun `a missing tile fails the whole lookup`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val provider = TerrariumElevationProvider(userAgent = "MockarrTest/0.0", baseUrl = server.url("/").toString())
        assertTrue(provider.elevations(listOf(LatLng(37.4, -122.2))).isFailure)
    }

    /** A minimal 8-bit RGB PNG encoder for fixtures: one filter type per row, filtered exactly per spec. */
    private fun encodePng(width: Int, height: Int, filters: IntArray, pixel: (Int, Int) -> IntArray): ByteArray {
        val bpp = 3
        val stride = width * bpp
        val rows = Array(height) { y ->
            ByteArray(stride).also { row ->
                for (x in 0 until width) pixel(x, y).forEachIndexed { c, v -> row[x * bpp + c] = v.toByte() }
            }
        }
        val raw = ByteArrayOutputStream()
        for (y in 0 until height) {
            val filter = filters[y]
            raw.write(filter)
            val cur = rows[y]
            val prev = if (y > 0) rows[y - 1] else ByteArray(stride)
            for (i in 0 until stride) {
                val x = cur[i].toInt() and 0xFF
                val a = if (i >= bpp) cur[i - bpp].toInt() and 0xFF else 0
                val b = prev[i].toInt() and 0xFF
                val c = if (i >= bpp) prev[i - bpp].toInt() and 0xFF else 0
                val predicted = when (filter) {
                    0 -> 0
                    1 -> a
                    2 -> b
                    3 -> (a + b) / 2
                    else -> paeth(a, b, c)
                }
                raw.write((x - predicted) and 0xFF)
            }
        }
        val deflater = Deflater()
        deflater.setInput(raw.toByteArray())
        deflater.finish()
        val compressed = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) compressed.write(buf, 0, deflater.deflate(buf))
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        val ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height).put(8).put(2).put(0).put(0).put(0).array()
        writeChunk(out, "IHDR", ihdr)
        writeChunk(out, "IDAT", compressed.toByteArray())
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        out.write(ByteBuffer.allocate(4).putInt(data.size).array())
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) {
            a
        } else if (pb <= pc) {
            b
        } else {
            c
        }
    }
}
