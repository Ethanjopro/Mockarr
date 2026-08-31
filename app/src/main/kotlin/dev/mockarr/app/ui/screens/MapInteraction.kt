package dev.mockarr.app.ui.screens

import androidx.compose.ui.geometry.Offset
import dev.mockarr.core.model.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The map's transient interaction state, owned by [MapViewModel]: which stop
 * is selected (and where its marker is on screen), a pending Move, and a
 * pending "from held spot / from route start" pick. None of it survives an
 * edit to the stops — the ViewModel resets it on every mutation.
 */
class MapInteraction(private val isStop: (Int) -> Boolean) {

    /** Index of the waypoint whose popover is open, or null. */
    private val _selectedWaypoint = MutableStateFlow<Int?>(null)
    val selectedWaypoint: StateFlow<Int?> = _selectedWaypoint.asStateFlow()

    /** Window position of the selected marker (fed by the map) for the stop popover. */
    private val _selectedMarkerScreen = MutableStateFlow<Offset?>(null)
    val selectedMarkerScreen: StateFlow<Offset?> = _selectedMarkerScreen.asStateFlow()

    /** Index of the stop the next map tap relocates (Strava's Move Point), or null. */
    private val _movingWaypoint = MutableStateFlow<Int?>(null)
    val movingWaypoint: StateFlow<Int?> = _movingWaypoint.asStateFlow()

    /** Route awaiting a "from held spot / from route start" pick in the action row. */
    private val _startChoiceRoute = MutableStateFlow<Route?>(null)
    val startChoiceRoute: StateFlow<Route?> = _startChoiceRoute.asStateFlow()

    /** The stop whose popover is open: a marker tap's selection; a sheet pick only highlights. */
    private val _popoverWaypoint = MutableStateFlow<Int?>(null)
    val popoverWaypoint: StateFlow<Int?> = _popoverWaypoint.asStateFlow()

    /**
     * Select a stop (or null to dismiss). A marker tap shows the popover; a
     * sheet row pick passes [showPopover] = false and only highlights the marker.
     */
    fun select(index: Int?, showPopover: Boolean = true) {
        _selectedWaypoint.value = index
        _popoverWaypoint.value = index.takeIf { showPopover }
        if (index == null) _selectedMarkerScreen.value = null
    }

    fun setMarkerScreen(point: Offset?) {
        _selectedMarkerScreen.value = point
    }

    /** Height in px of the overlay stack rising from the map's bottom edge (sheet peek, card, pills). */
    private val _overlayBottomPx = MutableStateFlow(0)
    val overlayBottomPx: StateFlow<Int> = _overlayBottomPx.asStateFlow()

    fun setOverlayBottom(px: Int) {
        _overlayBottomPx.value = px
    }

    /**
     * True while the search UI owns the next map tap (field focused or its
     * list open): that tap dismisses the search instead of placing a stop.
     * Mirrored in by MapScreen — the search ViewModel is scoped to the Map
     * destination and invisible to the map layer behind the NavHost.
     */
    private val _searchOwnsTaps = MutableStateFlow(false)
    val searchOwnsTaps: StateFlow<Boolean> = _searchOwnsTaps.asStateFlow()

    fun setSearchOwnsTaps(owns: Boolean) {
        _searchOwnsTaps.value = owns
    }

    /** Bumped when the map swallowed a tap to close the search; MapScreen reacts. */
    private val _searchDismissTicks = MutableStateFlow(0)
    val searchDismissTicks: StateFlow<Int> = _searchDismissTicks.asStateFlow()

    fun requestSearchDismiss() {
        _searchDismissTicks.value += 1
    }

    /** Strava's Move Point: the next map tap relocates this stop. */
    fun beginMove(index: Int) {
        if (!isStop(index)) return
        _movingWaypoint.value = index
        select(index)
    }

    fun cancelMove() {
        _movingWaypoint.value = null
        select(null)
    }

    /**
     * What the next plain map tap should do, in priority order: settle a pending
     * Move, dismiss the start-choice pills, dismiss the open selection, or add a
     * stop. Pure read — the caller performs the action (dismissal must not also
     * drop a stop, so deciding and acting are kept separate).
     */
    fun tapAction(): TapAction {
        val moving = _movingWaypoint.value
        return when {
            moving != null -> TapAction.MoveStop(moving)
            _startChoiceRoute.value != null -> TapAction.DismissStartChoice
            _selectedWaypoint.value != null -> TapAction.DismissSelection
            else -> TapAction.AddStop
        }
    }

    fun requestStartChoice(route: Route) {
        _startChoiceRoute.value = route
    }

    fun clearStartChoice() {
        _startChoiceRoute.value = null
    }

    /** Every stop edit drops the selection and any pending move. */
    fun reset() {
        _movingWaypoint.value = null
        select(null)
    }
}

/** The decision for a plain map tap — see [MapInteraction.tapAction]. */
sealed interface TapAction {
    /** A pending Move owns the tap: relocate this stop. */
    data class MoveStop(val index: Int) : TapAction

    /** The "from held spot / from route start" pills are up: this tap only closes them. */
    data object DismissStartChoice : TapAction

    /** A stop popover/selection is open: this tap only dismisses it. */
    data object DismissSelection : TapAction

    /** Nothing to dismiss: the tap drops a stop. */
    data object AddStop : TapAction
}
