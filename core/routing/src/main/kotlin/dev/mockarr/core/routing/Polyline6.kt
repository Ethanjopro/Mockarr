package dev.mockarr.core.routing

import dev.mockarr.core.model.LatLng

/**
 * Encoder/decoder for the Google polyline format at 1e-6 precision, which is
 * what OSRM emits for `geometries=polyline6`.
 */
object Polyline6 {

    private const val PRECISION = 1e6
    private const val CHUNK_MASK = 0x1f
    private const val CONTINUATION = 0x20
    private const val OFFSET = 63

    fun decode(encoded: String): List<LatLng> {
        val points = mutableListOf<LatLng>()
        var index = 0
        var lat = 0L
        var lng = 0L
        while (index < encoded.length) {
            val (dLat, afterLat) = decodeValue(encoded, index)
            val (dLng, afterLng) = decodeValue(encoded, afterLat)
            lat += dLat
            lng += dLng
            index = afterLng
            points += LatLng(lat / PRECISION, lng / PRECISION)
        }
        return points
    }

    fun encode(points: List<LatLng>): String {
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLng = 0L
        for (point in points) {
            val lat = Math.round(point.latitude * PRECISION)
            val lng = Math.round(point.longitude * PRECISION)
            encodeValue(lat - prevLat, sb)
            encodeValue(lng - prevLng, sb)
            prevLat = lat
            prevLng = lng
        }
        return sb.toString()
    }

    private fun decodeValue(encoded: String, start: Int): Pair<Long, Int> {
        var index = start
        var shift = 0
        var accumulator = 0L
        var chunk: Int
        do {
            chunk = encoded[index++].code - OFFSET
            accumulator = accumulator or ((chunk.toLong() and CHUNK_MASK.toLong()) shl shift)
            shift += 5
        } while (chunk >= CONTINUATION)
        val value = if (accumulator and 1L != 0L) (accumulator shr 1).inv() else accumulator shr 1
        return value to index
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= CONTINUATION) {
            sb.append(((CONTINUATION or (v and CHUNK_MASK.toLong()).toInt()) + OFFSET).toChar())
            v = v shr 5
        }
        sb.append((v.toInt() + OFFSET).toChar())
    }
}
