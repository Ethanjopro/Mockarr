package dev.mockarr.app.ui.map

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.OffRoadSpan
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitBySpansTest {

    private val points = (0..9).map { LatLng(48.0 + it * 0.01, 2.0) }

    @Test
    fun `no spans keeps the whole line on the road`() {
        val (road, offRoad) = splitBySpans(points, emptyList())
        assertEquals(listOf(points), road)
        assertEquals(emptyList(), offRoad)
    }

    @Test
    fun `a tail span splits the line at a shared point`() {
        val (road, offRoad) = splitBySpans(points, listOf(OffRoadSpan(7, 9)))
        assertEquals(listOf(points.subList(0, 8)), road)
        assertEquals(listOf(points.subList(7, 10)), offRoad)
    }

    @Test
    fun `a mid-route out-and-back span leaves road on both sides`() {
        val (road, offRoad) = splitBySpans(points, listOf(OffRoadSpan(3, 6)))
        assertEquals(listOf(points.subList(0, 4), points.subList(6, 10)), road)
        assertEquals(listOf(points.subList(3, 7)), offRoad)
    }

    @Test
    fun `a leading span starts the line off-road`() {
        val (road, offRoad) = splitBySpans(points, listOf(OffRoadSpan(0, 2)))
        assertEquals(listOf(points.subList(2, 10)), road)
        assertEquals(listOf(points.subList(0, 3)), offRoad)
    }

    @Test
    fun `out-of-range spans are clamped instead of crashing`() {
        val (road, offRoad) = splitBySpans(points, listOf(OffRoadSpan(8, 42)))
        assertEquals(listOf(points.subList(0, 9)), road)
        assertEquals(listOf(points.subList(8, 10)), offRoad)
    }
}
