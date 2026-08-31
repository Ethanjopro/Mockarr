package dev.mockarr.app.ui.map

import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.OffRoadSpan
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString

/**
 * The road stretches draw solid (ROUTE_SOURCE); off-road connectors draw as
 * blue dots (OFF_ROAD_SOURCE); a straight-line fallback draws the whole route
 * on FALLBACK_SOURCE so its amber keeps signalling "routing failed".
 */
internal fun updateRoute(
    style: Style,
    routePoints: List<LatLng>,
    isFallback: Boolean,
    offRoadSpans: List<OffRoadSpan>,
) {
    val none = emptyList<List<LatLng>>()
    val (roadLines, offRoadLines) = when {
        routePoints.size < 2 || isFallback -> none to none
        else -> splitBySpans(routePoints, offRoadSpans)
    }
    val fallbackLines = if (isFallback && routePoints.size >= 2) listOf(routePoints) else none
    style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(lineCollection(roadLines))
    style.getSourceAs<GeoJsonSource>(FALLBACK_SOURCE)?.setGeoJson(lineCollection(fallbackLines))
    style.getSourceAs<GeoJsonSource>(OFF_ROAD_SOURCE)?.setGeoJson(lineCollection(offRoadLines))
}

private fun lineCollection(lines: List<List<LatLng>>): FeatureCollection =
    FeatureCollection.fromFeatures(
        lines.filter { it.size >= 2 }
            .map { line -> Feature.fromGeometry(LineString.fromLngLats(line.map { it.toPoint() })) },
    )

/** Road slices (the spans' complement) and off-road slices of [points]; neighbours share endpoints. */
internal fun splitBySpans(
    points: List<LatLng>,
    spans: List<OffRoadSpan>,
): Pair<List<List<LatLng>>, List<List<LatLng>>> {
    if (spans.isEmpty()) return listOf(points) to emptyList()
    val road = mutableListOf<List<LatLng>>()
    val offRoad = mutableListOf<List<LatLng>>()
    var cursor = 0
    for (span in spans.sortedBy { it.start }) {
        val start = span.start.coerceIn(cursor, points.lastIndex)
        val end = span.end.coerceIn(start, points.lastIndex)
        if (start > cursor) road += points.subList(cursor, start + 1)
        if (end > start) offRoad += points.subList(start, end + 1)
        cursor = end
    }
    if (cursor < points.lastIndex) road += points.subList(cursor, points.size)
    return road to offRoad
}
