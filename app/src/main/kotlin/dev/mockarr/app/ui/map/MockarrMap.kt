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
import dev.mockarr.core.model.MapCamera
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MapLibreLatLng

private const val ROUTE_SOURCE = "route-source"
private const val ROUTE_LAYER = "route-layer"
private const val FALLBACK_SOURCE = "fallback-source"
private const val FALLBACK_LAYER = "fallback-layer"
private const val WAYPOINT_SOURCE = "waypoint-source"
private const val WAYPOINT_LAYER = "waypoint-layer"
private const val WAYPOINT_LABEL_LAYER = "waypoint-label-layer"
private const val PIN_SOURCE = "pin-source"
private const val PIN_LAYER = "pin-layer"
private const val PLAYBACK_SOURCE = "playback-source"
private const val PLAYBACK_LAYER = "playback-layer"
private const val ROLE_KEY = "role"
private const val LABEL_KEY = "label"

// The liberty style's flat building-footprint layer (outlined fills, z13-14).
private const val FLAT_BUILDING_LAYER = "building"
private const val EXTENDED_MAX_ZOOM = 24f

/**
 * MapLibre map with waypoint markers, the route polyline, the hold pin, and
 * the live playback dot. Hosted ONCE behind the NavHost (see MockarrApp) so it
 * survives tab switches — default SurfaceView rendering (fast path); opaque
 * screens simply cover it. Never animate this composable's alpha (SurfaceView
 * ignores it) and never toggle its visibility (that would destroy the surface).
 */
@Composable
fun MockarrMap(
    waypoints: List<LatLng>,
    routePoints: List<LatLng>,
    routeIsFallback: Boolean,
    onMapTap: (LatLng) -> Unit,
    onMapLongPress: (LatLng) -> Unit,
    styleUrl: String,
    visible: Boolean,
    loadInitialCamera: suspend () -> MapCamera?,
    onCameraIdle: (MapCamera) -> Unit,
    onUserGesture: () -> Unit,
    threeDimensional: Boolean,
    cameraCommand: CameraCommand? = null,
    pinPosition: LatLng? = null,
    playbackPosition: LatLng? = null,
    cameraFollow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnTap by rememberUpdatedState(onMapTap)
    val currentOnLongPress by rememberUpdatedState(onMapLongPress)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val currentOnUserGesture by rememberUpdatedState(onUserGesture)
    val currentVisible by rememberUpdatedState(visible)
    val currentLoadInitialCamera by rememberUpdatedState(loadInitialCamera)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var userMovedCamera by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { libreMap ->
                libreMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(FALLBACK_CENTER.toMapLibre(), FALLBACK_ZOOM),
                )
                libreMap.addOnMapClickListener { p ->
                    if (currentVisible) currentOnTap(LatLng(p.latitude, p.longitude))
                    currentVisible
                }
                libreMap.addOnMapLongClickListener { p ->
                    if (currentVisible) currentOnLongPress(LatLng(p.latitude, p.longitude))
                    currentVisible
                }
                libreMap.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        userMovedCamera = true
                        currentOnUserGesture()
                    }
                }
                libreMap.addOnCameraIdleListener {
                    val position = libreMap.cameraPosition
                    val target = position.target ?: return@addOnCameraIdleListener
                    currentOnCameraIdle(
                        MapCamera(LatLng(target.latitude, target.longitude), position.zoom),
                    )
                }
                map = libreMap
            }
        }
    }

    // START/STOP follow the activity; RESUME requires the map to actually be on
    // screen, so the render thread rests while another tab covers it.
    var lifecycleStarted by remember { mutableStateOf(false) }
    var mapResumed by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mapView.onStart()
                    lifecycleStarted = true
                }
                Lifecycle.Event.ON_STOP -> {
                    if (mapResumed) {
                        mapView.onPause()
                        mapResumed = false
                    }
                    mapView.onStop()
                    lifecycleStarted = false
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    LaunchedEffect(lifecycleStarted, visible) {
        if (lifecycleStarted && visible && !mapResumed) {
            mapView.onResume()
            mapResumed = true
        } else if (mapResumed && !(lifecycleStarted && visible)) {
            mapView.onPause()
            mapResumed = false
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    // One-shot cold-start restore, skipped if the user already panned away.
    LaunchedEffect(map) {
        val libreMap = map ?: return@LaunchedEffect
        val camera = currentLoadInitialCamera() ?: return@LaunchedEffect
        if (!userMovedCamera) {
            libreMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(camera.target.toMapLibre(), camera.zoom),
            )
        }
    }

    // (Re)load the style whenever the URL changes; sources/layers must be re-added after each load.
    var appliedStyleUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(map, styleUrl) {
        val libreMap = map ?: return@LaunchedEffect
        if (appliedStyleUrl != styleUrl) {
            appliedStyleUrl = styleUrl
            style = null
            libreMap.setStyle(Style.Builder().fromUri(styleUrl)) { loadedStyle ->
                setUpLayers(loadedStyle)
                style = loadedStyle
            }
        }
    }

    var flatBuildingMaxZoom by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(style, threeDimensional) {
        val loadedStyle = style ?: return@LaunchedEffect
        if (flatBuildingMaxZoom == null) {
            flatBuildingMaxZoom = loadedStyle.getLayer(FLAT_BUILDING_LAYER)?.maxZoom
        }
        applyMapMode(loadedStyle, map, threeDimensional, flatBuildingMaxZoom)
    }

    LaunchedEffect(style, waypoints) {
        val loadedStyle = style ?: return@LaunchedEffect
        updateWaypoints(loadedStyle, waypoints)
    }

    LaunchedEffect(style, routePoints, routeIsFallback) {
        val loadedStyle = style ?: return@LaunchedEffect
        updateRoute(loadedStyle, routePoints, routeIsFallback)
    }

    LaunchedEffect(style, pinPosition) {
        val loadedStyle = style ?: return@LaunchedEffect
        loadedStyle.getSourceAs<GeoJsonSource>(PIN_SOURCE)?.setGeoJson(pinPosition.toFeatures())
    }

    LaunchedEffect(style, playbackPosition) {
        val loadedStyle = style ?: return@LaunchedEffect
        loadedStyle.getSourceAs<GeoJsonSource>(PLAYBACK_SOURCE)
            ?.setGeoJson(playbackPosition.toFeatures())
    }

    LaunchedEffect(map, cameraCommand) {
        val libreMap = map ?: return@LaunchedEffect
        val command = cameraCommand ?: return@LaunchedEffect
        libreMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(command.target.toMapLibre(), command.zoom),
        )
    }

    LaunchedEffect(map, routePoints) {
        val libreMap = map ?: return@LaunchedEffect
        if (routePoints.size >= 2) {
            val bounds = LatLngBounds.Builder()
            routePoints.forEach { bounds.include(it.toMapLibre()) }
            libreMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), CAMERA_PADDING))
        }
    }

    LaunchedEffect(map, playbackPosition, cameraFollow) {
        val libreMap = map ?: return@LaunchedEffect
        val position = playbackPosition ?: return@LaunchedEffect
        if (cameraFollow) {
            val zoom = maxOf(libreMap.cameraPosition.zoom, FOLLOW_MIN_ZOOM)
            libreMap.easeCamera(
                CameraUpdateFactory.newLatLngZoom(position.toMapLibre(), zoom),
                FOLLOW_EASE_MILLIS,
            )
        }
    }
}

private const val CAMERA_PADDING = 120
private const val FOLLOW_MIN_ZOOM = 15.0
private const val FOLLOW_EASE_MILLIS = 900

// World-landmark fallback for a fresh install with no saved camera yet.
private val FALLBACK_CENTER = LatLng(48.8584, 2.2945)
private const val FALLBACK_ZOOM = 12.0

private fun LatLng.toMapLibre() = MapLibreLatLng(latitude, longitude)

private fun LatLng.toPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun LatLng?.toFeatures(): FeatureCollection = this?.let {
    FeatureCollection.fromFeature(Feature.fromGeometry(it.toPoint()))
} ?: FeatureCollection.fromFeatures(emptyList())

/**
 * 2D hides the style's building extrusions, extends the flat building-footprint
 * layer to all zooms (the style normally hands off to extrusions at z14, which
 * would leave 2D with no buildings at all), and flattens/locks the camera tilt.
 */
private fun applyMapMode(
    style: Style,
    map: MapLibreMap?,
    threeDimensional: Boolean,
    flatBuildingOriginalMaxZoom: Float?,
) {
    val visibility = if (threeDimensional) Property.VISIBLE else Property.NONE
    style.layers.filterIsInstance<FillExtrusionLayer>().forEach { layer ->
        layer.setProperties(PropertyFactory.visibility(visibility))
    }
    style.getLayer(FLAT_BUILDING_LAYER)?.let { flat ->
        flat.maxZoom = if (threeDimensional) {
            flatBuildingOriginalMaxZoom ?: flat.maxZoom
        } else {
            EXTENDED_MAX_ZOOM
        }
    }
    val libreMap = map ?: return
    libreMap.uiSettings.isTiltGesturesEnabled = threeDimensional
    if (!threeDimensional && libreMap.cameraPosition.tilt > 0.0) {
        libreMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(libreMap.cameraPosition).tilt(0.0).build(),
            ),
        )
    }
}

private fun setUpLayers(style: Style) {
    listOf(ROUTE_SOURCE, FALLBACK_SOURCE, WAYPOINT_SOURCE, PIN_SOURCE, PLAYBACK_SOURCE)
        .forEach { style.addSource(GeoJsonSource(it)) }
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
    addPointLayers(style)
}

private fun addPointLayers(style: Style) {
    style.addLayer(
        CircleLayer(PLAYBACK_LAYER, PLAYBACK_SOURCE).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor("#1A73E8"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
    style.addLayer(
        CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor("#8E24AA"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
    style.addLayer(
        CircleLayer(WAYPOINT_LAYER, WAYPOINT_SOURCE).withProperties(
            PropertyFactory.circleRadius(11f),
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
    style.addLayer(
        SymbolLayer(WAYPOINT_LABEL_LAYER, WAYPOINT_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get(LABEL_KEY)),
            PropertyFactory.textSize(13f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
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
            addStringProperty(LABEL_KEY, (index + 1).toString())
        }
    }
    style.getSourceAs<GeoJsonSource>(WAYPOINT_SOURCE)
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
    style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(if (isFallback) empty else line)
    style.getSourceAs<GeoJsonSource>(FALLBACK_SOURCE)?.setGeoJson(if (isFallback) line else empty)
}
