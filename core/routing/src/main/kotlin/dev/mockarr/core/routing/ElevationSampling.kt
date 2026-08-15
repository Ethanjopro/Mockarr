package dev.mockarr.core.routing

import dev.mockarr.core.model.GeoMath
import dev.mockarr.core.model.LatLng

/**
 * Picks which route vertices to query for elevation (routes can have far more
 * points than one API batch) and spreads the results back over every vertex.
 * Sampling is even by along-track distance, not by index — OSRM vertex density
 * varies wildly between straights and curves.
 */
object ElevationSampling {

    /** Indices to sample: always both ends, at most [maxSamples], distance-even. */
    fun sampleIndices(points: List<LatLng>, maxSamples: Int = DEFAULT_MAX_SAMPLES): List<Int> {
        require(maxSamples >= 2) { "Need at least the two endpoints" }
        if (points.size <= maxSamples) return points.indices.toList()
        val cumulative = cumulativeDistances(points)
        val total = cumulative.last()
        val indices = ArrayList<Int>(maxSamples)
        var cursor = 0
        for (s in 0 until maxSamples) {
            val target = total * s / (maxSamples - 1)
            while (cursor < points.size - 1 && cumulative[cursor] < target) cursor++
            if (indices.lastOrNull() != cursor) indices.add(cursor)
        }
        if (indices.last() != points.size - 1) indices.add(points.size - 1)
        return indices
    }

    /**
     * Expands sampled elevations to all vertices by linear interpolation along
     * track distance. Returns a list aligned 1:1 with [points].
     */
    fun interpolate(
        points: List<LatLng>,
        sampledIndices: List<Int>,
        sampledElevations: List<Double>,
    ): List<Double> {
        require(sampledIndices.size == sampledElevations.size) { "Samples misaligned" }
        require(sampledIndices.isNotEmpty()) { "Need at least one sample" }
        val cumulative = cumulativeDistances(points)
        val result = ArrayList<Double>(points.size)
        var upper = 0
        for (i in points.indices) {
            while (upper < sampledIndices.size - 1 && sampledIndices[upper] < i) upper++
            val hiIndex = sampledIndices[upper]
            val loIndex = sampledIndices[(upper - 1).coerceAtLeast(0)]
            val hi = sampledElevations[upper]
            val lo = sampledElevations[(upper - 1).coerceAtLeast(0)]
            val span = cumulative[hiIndex] - cumulative[loIndex]
            result += if (i <= loIndex || span <= 0.0) {
                lo
            } else if (i >= hiIndex) {
                hi
            } else {
                val t = (cumulative[i] - cumulative[loIndex]) / span
                lo + (hi - lo) * t
            }
        }
        return result
    }

    private fun cumulativeDistances(points: List<LatLng>): DoubleArray {
        val cumulative = DoubleArray(points.size)
        for (i in 0 until points.size - 1) {
            cumulative[i + 1] = cumulative[i] + GeoMath.distanceMeters(points[i], points[i + 1])
        }
        return cumulative
    }

    private const val DEFAULT_MAX_SAMPLES = 100
}
