package dev.mockarr.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mockarr.core.model.LatLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val ROUTE_SOURCE = "route-source"
private const val ROUTE_LAYER = "route-layer"
private const val FALLBACK_SOURCE = "fallback-source"
private const val FALLBACK_LAYER = "fallback-layer"
private const val WAYPOINT_SOURCE = "waypoint-source"
private const val WAYPOINT_LAYER = "waypoint-layer"
private const val ROLE_KEY = "role"

/** MapLibre map with waypoint markers and the route polyline. */
@Composable
fun MockarrMap(
    waypoints: List<LatLng>,
    routePoints: List<LatLng>,
    routeIsFallback: Boolean,
    onMapTap: (LatLng) -> Unit,
    onMapLongPress: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnTap by rememberUpdatedState(onMapTap)
    val currentOnLongPress by rememberUpdatedState(onMapLongPress)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { libreMap ->
                libreMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(INITIAL_CENTER.toMapLibre(), INITIAL_ZOOM),
                )
                libreMap.addOnMapClickListener { p ->
                    currentOnTap(LatLng(p.latitude, p.longitude))
                    true
                }
                libreMap.addOnMapLongClickListener { p ->
                    currentOnLongPress(LatLng(p.latitude, p.longitude))
                    true
                }
                libreMap.setStyle(Style.Builder().fromUri(STYLE_URL)) { loadedStyle ->
                    setUpLayers(loadedStyle)
                    style = loadedStyle
                }
                map = libreMap
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    LaunchedEffect(style, waypoints, routePoints, routeIsFallback) {
        val loadedStyle = style ?: return@LaunchedEffect
        updateWaypoints(loadedStyle, waypoints)
        updateRoute(loadedStyle, routePoints, routeIsFallback)
    }

    LaunchedEffect(map, routePoints) {
        val libreMap = map ?: return@LaunchedEffect
        if (routePoints.size >= 2) {
            val bounds = LatLngBounds.Builder()
            routePoints.forEach { bounds.include(it.toMapLibre()) }
            libreMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), CAMERA_PADDING))
        }
    }
}

private const val CAMERA_PADDING = 120

// Placeholder start view until real-location centering lands (M5)
private val INITIAL_CENTER = LatLng(48.8584, 2.2945)
private const val INITIAL_ZOOM = 12.0

private fun LatLng.toMapLibre() = org.maplibre.android.geometry.LatLng(latitude, longitude)

private fun LatLng.toPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun setUpLayers(style: Style) {
    style.addSource(org.maplibre.android.style.sources.GeoJsonSource(ROUTE_SOURCE))
    style.addSource(org.maplibre.android.style.sources.GeoJsonSource(FALLBACK_SOURCE))
    style.addSource(org.maplibre.android.style.sources.GeoJsonSource(WAYPOINT_SOURCE))
    style.addLayer(
        LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#1A73E8"),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer(FALLBACK_LAYER, FALLBACK_SOURCE).withProperties(
            PropertyFactory.lineColor("#EA8600"),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f)),
        ),
    )
    style.addLayer(
        CircleLayer(WAYPOINT_LAYER, WAYPOINT_SOURCE).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor(
                Expression.match(
                    Expression.get(ROLE_KEY),
                    Expression.rgb(69, 90, 100),
                    Expression.stop("start", Expression.rgb(46, 125, 50)),
                    Expression.stop("end", Expression.rgb(198, 40, 40)),
                ),
            ),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2.5f),
        ),
    )
}

private fun updateWaypoints(style: Style, waypoints: List<LatLng>) {
    val features = waypoints.mapIndexed { index, latLng ->
        Feature.fromGeometry(latLng.toPoint()).apply {
            val role = when (index) {
                0 -> "start"
                waypoints.lastIndex -> "end"
                else -> "via"
            }
            addStringProperty(ROLE_KEY, role)
        }
    }
    style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(WAYPOINT_SOURCE)
        ?.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun updateRoute(style: Style, routePoints: List<LatLng>, isFallback: Boolean) {
    val line = if (routePoints.size >= 2) {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(LineString.fromLngLats(routePoints.map { it.toPoint() })),
        )
    } else {
        FeatureCollection.fromFeatures(emptyList())
    }
    val empty = FeatureCollection.fromFeatures(emptyList())
    style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(ROUTE_SOURCE)
        ?.setGeoJson(if (isFallback) empty else line)
    style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(FALLBACK_SOURCE)
        ?.setGeoJson(if (isFallback) line else empty)
}
