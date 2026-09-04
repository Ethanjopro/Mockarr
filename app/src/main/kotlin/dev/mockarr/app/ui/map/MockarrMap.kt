package dev.mockarr.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mockarr.app.R
import dev.mockarr.app.ui.theme.MapPalette
import dev.mockarr.app.ui.theme.Tokens
import dev.mockarr.core.model.LatLng
import dev.mockarr.core.model.MapCamera
import dev.mockarr.core.model.OffRoadSpan
import dev.mockarr.core.model.Waypoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
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
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.roundToInt
import org.maplibre.android.geometry.LatLng as MapLibreLatLng

internal const val ROUTE_SOURCE = "route-source"
private const val ROUTE_LAYER = "route-layer"
private const val ROUTE_CASING_LAYER = "route-casing-layer"
private const val ROUTE_ARROW_LAYER = "route-arrow-layer"
private const val ROUTE_ARROW_ICON = "route-chevron"
private const val ROUTE_ARROW_SPACING_DP = 72f
private const val CHEVRON_SIZE_DP = 10f
private const val CHEVRON_STROKE_DP = 2f
internal const val FALLBACK_SOURCE = "fallback-source"
private const val FALLBACK_LAYER = "fallback-layer"
internal const val OFF_ROAD_SOURCE = "off-road-source"
private const val OFF_ROAD_LAYER = "off-road-layer"
internal const val WAYPOINT_SOURCE = "waypoint-source"
internal const val WAYPOINT_LAYER = "waypoint-layer"
internal const val WAIT_CHIP_SOURCE = "wait-chip-source"
internal const val WAIT_CHIP_LAYER = "wait-chip-layer"
private const val PIN_SOURCE = "pin-source"
private const val PIN_LAYER = "pin-layer"
private const val PLAYBACK_SOURCE = "playback-source"
private const val PLAYBACK_LAYER = "playback-layer"
internal const val ICON_KEY = "icon"
internal const val SORT_KEY = "sort"
internal const val RANK_KEY = "rank"

// The liberty style's flat building-footprint layer (outlined fills, z13-14).
private const val FLAT_BUILDING_LAYER = "building"
private const val EXTENDED_MAX_ZOOM = 24f

// Gentle auto-pitch when 3D is toggled on; buildings stay the style's stock look.
private const val ENTER_3D_TILT_DEGREES = 30.0

/**
 * MapLibre map with waypoint markers, the route polyline, the hold pin, and
 * the live playback dot. Hosted ONCE behind the NavHost (see MockarrApp) so it
 * survives tab switches — default SurfaceView rendering (fast path); opaque
 * screens simply cover it. Never animate this composable's alpha (SurfaceView
 * ignores it) and never toggle its visibility (that would destroy the surface).
 */
@Composable
fun MockarrMap(
    waypoints: List<Waypoint>,
    routePoints: List<LatLng>,
    routeIsFallback: Boolean,
    offRoadSpans: List<OffRoadSpan>,
    onMapTap: (LatLng) -> Unit,
    onWaypointTap: (Int) -> Unit,
    onWaypointDrag: (Int, LatLng) -> Unit,
    onWaypointDrop: (Int, LatLng) -> Unit,
    onMapLongPress: (LatLng) -> Unit,
    onSelectedWaypointScreen: (Offset?) -> Unit,
    styleUrl: String,
    palette: MapPalette,
    visible: Boolean,
    loadInitialCamera: suspend () -> MapCamera?,
    onCameraIdle: (MapCamera) -> Unit,
    onCameraMove: (MapCamera) -> Unit,
    onUserGesture: () -> Unit,
    threeDimensional: Boolean,
    selectedWaypoint: Int? = null,
    activeDwell: ActiveDwell? = null,
    cameraCommand: CameraCommand? = null,
    bottomObstructionPx: Int = 0,
    pinPosition: LatLng? = null,
    searchedPlace: LatLng? = null,
    onSearchPinTap: () -> Unit = {},
    playbackPosition: LatLng? = null,
    cameraFollow: Boolean = false,
    animateCamera: Boolean = true,
    dragEnabled: Boolean = true,
    draggableWaypoint: Int? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnTap by rememberUpdatedState(onMapTap)
    val currentOnWaypointTap by rememberUpdatedState(onWaypointTap)
    val currentOnWaypointDrag by rememberUpdatedState(onWaypointDrag)
    val currentOnWaypointDrop by rememberUpdatedState(onWaypointDrop)
    val currentOnLongPress by rememberUpdatedState(onMapLongPress)
    val currentSearchedPlace by rememberUpdatedState(searchedPlace)
    val currentOnSearchPinTap by rememberUpdatedState(onSearchPinTap)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val currentOnCameraMove by rememberUpdatedState(onCameraMove)
    val currentOnUserGesture by rememberUpdatedState(onUserGesture)
    val currentOnSelectedWaypointScreen by rememberUpdatedState(onSelectedWaypointScreen)
    val currentVisible by rememberUpdatedState(visible)
    val currentWaypoints by rememberUpdatedState(waypoints)
    val currentSelectedWaypoint by rememberUpdatedState(selectedWaypoint)
    val currentLoadInitialCamera by rememberUpdatedState(loadInitialCamera)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var dragHandler by remember { mutableStateOf<MarkerDragHandler?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var userMovedCamera by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { libreMap ->
                libreMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(FALLBACK_CENTER.toMapLibre(), FALLBACK_ZOOM),
                )
                // Touches that start on a marker are owned by the drag handler
                // (tap = select, drag = move); the click listener sees map taps only.
                // Hit-test against the drawn discs (not the icon quads), with
                // whatever the stops and selection are at touch time.
                val hitTest = { p: org.maplibre.android.geometry.LatLng ->
                    libreMap.waypointIndexAt(p, currentWaypoints, currentSelectedWaypoint, density)
                }
                val handler = MarkerDragHandler(
                    map = libreMap,
                    hitTest = hitTest,
                    slopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
                    onTap = { currentOnWaypointTap(it) },
                    onDrag = { index, point -> currentOnWaypointDrag(index, point) },
                    onDrop = { index, point -> currentOnWaypointDrop(index, point) },
                )
                @Suppress("ClickableViewAccessibility")
                setOnTouchListener(handler)
                dragHandler = handler
                libreMap.addOnMapClickListener { p ->
                    if (currentVisible) {
                        val tapped = hitTest(p)
                        val pin = currentSearchedPlace
                        when {
                            tapped != null -> currentOnWaypointTap(tapped)
                            // The searched place's pin: the stop lands on the exact geocoded point.
                            pin != null && libreMap.searchPinAt(p, pin, density) -> currentOnSearchPinTap()
                            else -> currentOnTap(LatLng(p.latitude, p.longitude))
                        }
                    }
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
                libreMap.addOnCameraMoveListener {
                    val position = libreMap.cameraPosition
                    val target = position.target ?: return@addOnCameraMoveListener
                    currentOnCameraMove(
                        MapCamera(LatLng(target.latitude, target.longitude), position.zoom),
                    )
                }
                libreMap.addOnCameraIdleListener {
                    val position = libreMap.cameraPosition
                    val target = position.target ?: return@addOnCameraIdleListener
                    currentOnCameraIdle(
                        MapCamera(LatLng(target.latitude, target.longitude), position.zoom),
                    )
                }
                // North-up always: rotate gestures are off, so MapLibre's default
                // compass — which sat invisible behind the search bar and "popped
                // up" after an accidental two-finger twist — can never appear.
                libreMap.uiSettings.isRotateGesturesEnabled = false
                libreMap.uiSettings.isCompassEnabled = false
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

    // The map view is a bare Android view to the accessibility tree: name it and
    // say where the gesture-free path is (the sheet's rows).
    val mapDescription = stringResource(R.string.map_cd)
    AndroidView(factory = { mapView }, modifier = modifier.semantics { contentDescription = mapDescription })

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
    var flatBuildingMaxZoom by remember { mutableStateOf<Float?>(null) }
    // Attribution "i" (ODbL / OpenFreeMap terms want it visible) keeps MapLibre's
    // bottom-left corner but rides the overlay stack (sheet peek, card, pills) so
    // it never hides under the sheet — the least conspicuous spot that stays on
    // screen in every state. The logo is a courtesy, not a licence term.
    LaunchedEffect(map, bottomObstructionPx, palette) {
        val libreMap = map ?: return@LaunchedEffect
        with(libreMap.uiSettings) {
            isLogoEnabled = false
            setAttributionGravity(Gravity.BOTTOM or Gravity.START)
            setAttributionMargins(
                (Tokens.mapEdge.value * density).roundToInt(),
                0,
                0,
                bottomObstructionPx + (Tokens.space2.value * density).roundToInt(),
            )
            setAttributionTintColor(palette.attribution)
        }
    }

    LaunchedEffect(map, styleUrl) {
        val libreMap = map ?: return@LaunchedEffect
        if (appliedStyleUrl != styleUrl) {
            appliedStyleUrl = styleUrl
            style = null
            // Each style defines its own building maxZoom — re-capture after reload.
            flatBuildingMaxZoom = null
            libreMap.setStyle(Style.Builder().fromUri(styleUrl)) { loadedStyle ->
                setUpLayers(loadedStyle, density, palette)
                style = loadedStyle
            }
        }
    }
    LaunchedEffect(style, threeDimensional) {
        val loadedStyle = style ?: return@LaunchedEffect
        if (flatBuildingMaxZoom == null) {
            flatBuildingMaxZoom = loadedStyle.getLayer(FLAT_BUILDING_LAYER)?.maxZoom
        }
        applyMapMode(loadedStyle, map, threeDimensional, flatBuildingMaxZoom)
    }

    // Theme switches re-tint the live layers in place; a style reload rebuilds them.
    LaunchedEffect(style, palette) {
        val loadedStyle = style ?: return@LaunchedEffect
        applyPalette(loadedStyle, palette, density)
    }

    val markerStyle = remember(density, palette) { MarkerStyle(density, palette) }
    LaunchedEffect(style, waypoints, selectedWaypoint, markerStyle) {
        val loadedStyle = style ?: return@LaunchedEffect
        updateWaypoints(loadedStyle, waypoints, selectedWaypoint, markerStyle)
    }
    LaunchedEffect(dragHandler, dragEnabled, draggableWaypoint) {
        dragHandler?.enabled = dragEnabled
        dragHandler?.draggableIndex = draggableWaypoint
    }
    // The selected marker's window position rides every camera frame so the
    // stop popover stays glued to it.
    val tracker = remember(map) { map?.let { MarkerTracker(it, mapView) { p -> currentOnSelectedWaypointScreen(p) } } }
    LaunchedEffect(tracker, waypoints, selectedWaypoint) {
        tracker?.track(selectedWaypoint?.let { waypoints.getOrNull(it)?.position })
    }

    // Keyed on style: a style reload drops its images, so the tracker restarts.
    val chipIcons = remember(style) { mutableSetOf<String>() }
    LaunchedEffect(style, waypoints, activeDwell, markerStyle) {
        val loadedStyle = style ?: return@LaunchedEffect
        updateWaitChips(loadedStyle, waypoints, activeDwell, markerStyle, chipIcons)
    }

    LaunchedEffect(style, routePoints, routeIsFallback, offRoadSpans) {
        val loadedStyle = style ?: return@LaunchedEffect
        updateRoute(loadedStyle, routePoints, routeIsFallback, offRoadSpans)
    }

    LaunchedEffect(style, pinPosition) {
        val loadedStyle = style ?: return@LaunchedEffect
        loadedStyle.getSourceAs<GeoJsonSource>(PIN_SOURCE)?.setGeoJson(pinPosition.toFeatures())
    }
    LaunchedEffect(style, searchedPlace) {
        val loadedStyle = style ?: return@LaunchedEffect
        loadedStyle.getSourceAs<GeoJsonSource>(SEARCH_PIN_SOURCE)?.setGeoJson(searchedPlace.toFeatures())
    }

    // Keep a nudged hold pin in view: when it crosses into the outer margin of
    // the viewport, ease the camera back onto it. A stationary pin never
    // triggers this — the effect only runs when pinPosition changes.
    LaunchedEffect(map, pinPosition) {
        val libreMap = map ?: return@LaunchedEffect
        val pin = pinPosition ?: return@LaunchedEffect
        val screen = libreMap.projection.toScreenLocation(pin.toMapLibre())
        val marginX = libreMap.width * KEEP_IN_VIEW_MARGIN
        val marginY = libreMap.height * KEEP_IN_VIEW_MARGIN
        val outside = screen.x < marginX || screen.x > libreMap.width - marginX ||
            screen.y < marginY || screen.y > libreMap.height - marginY
        if (outside) {
            libreMap.move(CameraUpdateFactory.newLatLng(pin.toMapLibre()), animateCamera, KEEP_IN_VIEW_EASE_MILLIS)
        }
    }

    LaunchedEffect(style, playbackPosition) {
        val loadedStyle = style ?: return@LaunchedEffect
        loadedStyle.getSourceAs<GeoJsonSource>(PLAYBACK_SOURCE)
            ?.setGeoJson(playbackPosition.toFeatures())
    }

    // Read at apply time, not as a key: a taller overlay must not re-run the last fit.
    val currentBottomObstruction by rememberUpdatedState(bottomObstructionPx)
    LaunchedEffect(map, cameraCommand) {
        val libreMap = map ?: return@LaunchedEffect
        val command = cameraCommand ?: return@LaunchedEffect
        applyCameraCommand(libreMap, command, FitPadding(density, currentBottomObstruction), animateCamera)
    }

    // Follow eases the camera to each fix. The zoom floor applies only when
    // follow first engages; after that the user's pinch zoom is respected, so
    // nothing ratchets in over a long route or lurches when playback ends.
    var followEngaged by remember { mutableStateOf(false) }
    LaunchedEffect(cameraFollow) {
        if (!cameraFollow) followEngaged = false
    }
    LaunchedEffect(map, playbackPosition, cameraFollow) {
        val libreMap = map ?: return@LaunchedEffect
        val position = playbackPosition ?: return@LaunchedEffect
        if (!cameraFollow) return@LaunchedEffect
        val update = if (followEngaged) {
            CameraUpdateFactory.newLatLng(position.toMapLibre())
        } else {
            followEngaged = true
            CameraUpdateFactory.newLatLngZoom(
                position.toMapLibre(),
                maxOf(libreMap.cameraPosition.zoom, FOLLOW_MIN_ZOOM),
            )
        }
        libreMap.move(update, animateCamera, FOLLOW_EASE_MILLIS)
    }
}

private const val CASING_OPACITY = 0.9f
private const val FOLLOW_MIN_ZOOM = 15.0
private const val FOLLOW_EASE_MILLIS = 900
private const val KEEP_IN_VIEW_MARGIN = 0.2f
private const val KEEP_IN_VIEW_EASE_MILLIS = 400

// World-landmark fallback for a fresh install with no saved camera yet.
private val FALLBACK_CENTER = LatLng(48.8584, 2.2945)
private const val FALLBACK_ZOOM = 12.0

internal fun LatLng.toMapLibre() = MapLibreLatLng(latitude, longitude)

internal fun LatLng.toPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun LatLng?.toFeatures(): FeatureCollection = this?.let {
    FeatureCollection.fromFeature(Feature.fromGeometry(it.toPoint()))
} ?: FeatureCollection.fromFeatures(emptyList())

/**
 * 2D hides the style's building extrusions, extends the flat building-footprint
 * layer to all zooms (the style normally hands off to extrusions at z14, which
 * would leave 2D with no buildings at all), and flattens/locks the camera tilt.
 * 3D pitches the camera by itself — before, the toggle looked identical to 2D
 * until the user two-finger tilted, which nothing suggested doing. A tilt the
 * user has already set by hand is left alone.
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
    val tilt = libreMap.cameraPosition.tilt
    val targetTilt = when {
        threeDimensional && tilt == 0.0 -> ENTER_3D_TILT_DEGREES
        !threeDimensional && tilt > 0.0 -> 0.0
        else -> return
    }
    libreMap.animateCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder(libreMap.cameraPosition).tilt(targetTilt).build(),
        ),
    )
}

private fun setUpLayers(style: Style, density: Float, palette: MapPalette) {
    // Line sources keep full geometry: the default geojson-vt options (tolerance
    // 0.375, maxZoom 18) re-simplify the route client-side, visibly cutting
    // corners off the road when overzoomed past z18.
    val lineSourceOptions = GeoJsonOptions().withMaxZoom(22).withTolerance(0.0f)
    listOf(ROUTE_SOURCE, FALLBACK_SOURCE, OFF_ROAD_SOURCE)
        .forEach { style.addSource(GeoJsonSource(it, lineSourceOptions)) }
    listOf(WAYPOINT_SOURCE, WAIT_CHIP_SOURCE, SEARCH_PIN_SOURCE, PIN_SOURCE, PLAYBACK_SOURCE)
        .forEach { style.addSource(GeoJsonSource(it)) }
    fun zoomWidth(z10: Float, z15: Float, z19: Float) = Expression.interpolate(
        Expression.exponential(1.5f),
        Expression.zoom(),
        Expression.stop(10, z10),
        Expression.stop(15, z15),
        Expression.stop(19, z19),
    )
    val routeWidth = zoomWidth(3f, 5f, 11f)
    val casingWidth = zoomWidth(5f, 8f, 15f)
    // Casing under the line: the route stays legible on any basemap tone.
    style.addLayer(
        LineLayer(ROUTE_CASING_LAYER, ROUTE_SOURCE).withProperties(
            PropertyFactory.lineWidth(casingWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(CASING_OPACITY),
        ),
    )
    style.addLayer(
        LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
            PropertyFactory.lineWidth(routeWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer(FALLBACK_LAYER, FALLBACK_SOURCE).withProperties(
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f)),
        ),
    )
    // Off-road connectors: dots at the road line's width so they read as route.
    style.addLayer(
        LineLayer(OFF_ROAD_LAYER, OFF_ROAD_SOURCE).withProperties(
            PropertyFactory.lineWidth(routeWidth),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineDasharray(arrayOf(0f, 2f)),
        ),
    )
    // Direction chevrons ride the line so a glance tells which way the drive goes.
    style.addLayer(
        SymbolLayer(ROUTE_ARROW_LAYER, ROUTE_SOURCE).withProperties(
            PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
            PropertyFactory.symbolSpacing(ROUTE_ARROW_SPACING_DP * density),
            PropertyFactory.iconImage(ROUTE_ARROW_ICON),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
        ),
    )
    addPointLayers(style)
    applyPalette(style, palette, density)
}

/** Colour every custom layer from [palette]; safe to call again on a theme change. */
private fun applyPalette(style: Style, palette: MapPalette, density: Float) {
    style.getLayer(ROUTE_CASING_LAYER)?.setProperties(
        PropertyFactory.lineColor(MapPalette.css(palette.routeCasing)),
    )
    style.getLayer(ROUTE_LAYER)?.setProperties(PropertyFactory.lineColor(MapPalette.css(palette.route)))
    style.getLayer(FALLBACK_LAYER)?.setProperties(
        PropertyFactory.lineColor(MapPalette.css(palette.fallbackRoute)),
    )
    style.getLayer(OFF_ROAD_LAYER)?.setProperties(
        PropertyFactory.lineColor(MapPalette.css(palette.offRoad)),
    )
    style.getLayer(PLAYBACK_LAYER)?.setProperties(
        PropertyFactory.circleColor(MapPalette.css(palette.position)),
        PropertyFactory.circleStrokeColor(MapPalette.css(palette.positionRing)),
    )
    style.getLayer(PIN_LAYER)?.setProperties(
        PropertyFactory.circleColor(MapPalette.css(palette.holdPin)),
        PropertyFactory.circleStrokeColor(MapPalette.css(palette.positionRing)),
    )
    style.addImage(ROUTE_ARROW_ICON, chevronBitmap(palette.routeCasing, density))
    style.addImage(SEARCH_PIN_ICON, searchPinBitmap(MarkerStyle(density, palette)))
}

/** A ">" pointing along +x; MapLibre rotates it to the line's bearing. */
private fun chevronBitmap(color: Int, density: Float): Bitmap {
    val size = (CHEVRON_SIZE_DP * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val stroke = CHEVRON_STROKE_DP * density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val inset = stroke
    val path = Path().apply {
        moveTo(inset, inset)
        lineTo(size - inset, size / 2f)
        lineTo(inset, size - inset)
    }
    Canvas(bitmap).drawPath(path, paint)
    return bitmap
}

private fun addPointLayers(style: Style) {
    // One symbol layer with the circle+number baked into each icon bitmap: a
    // CircleLayer + text SymbolLayer pair draws ALL circles beneath ALL numbers,
    // so overlapping markers showed the lower marker's number on the upper circle.
    style.addLayer(
        SymbolLayer(WAYPOINT_LAYER, WAYPOINT_SOURCE).withProperties(
            PropertyFactory.iconImage(Expression.get(ICON_KEY)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.symbolSortKey(Expression.get(RANK_KEY)),
        ),
    )
    // Wait chips float above their markers: bottom-anchored at the waypoint,
    // lifted clear of the marker circle. icon-offset is in dp (× icon-size),
    // NOT bitmap pixels — multiplying by density here lifted the chip ~60 dp.
    style.addLayer(
        SymbolLayer(WAIT_CHIP_LAYER, WAIT_CHIP_SOURCE).withProperties(
            PropertyFactory.iconImage(Expression.get(ICON_KEY)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconOffset(arrayOf(0f, -WAIT_CHIP_LIFT_DP)),
        ),
    )
    // The searched place: a teardrop whose tip is the geocoded point, over the
    // stop discs (a stop dropped on it hides it anyway) and under the mocked location.
    style.addLayer(
        SymbolLayer(SEARCH_PIN_LAYER, SEARCH_PIN_SOURCE).withProperties(
            PropertyFactory.iconImage(SEARCH_PIN_ICON),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        ),
    )
    // The mocked location is the top of the stack: the hold pin and the live
    // dot draw over stop discs and wait chips, never under them.
    style.addLayer(
        CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
    style.addLayer(
        CircleLayer(PLAYBACK_LAYER, PLAYBACK_SOURCE).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}
