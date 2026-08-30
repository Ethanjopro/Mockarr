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

    /** True while the selection came from the sheet: highlight the marker, keep the popover shut. */
    private val _popoverHidden = MutableStateFlow(false)
    val popoverHidden: StateFlow<Boolean> = _popoverHidden.asStateFlow()

    /**
     * Select a stop (or null to dismiss). A marker tap shows the popover; a
     * sheet row pick passes [showPopover] = false and only highlights the marker.
     */
    fun select(index: Int?, showPopover: Boolean = true) {
        _selectedWaypoint.value = index
        _popoverHidden.value = index != null && !showPopover
        if (index == null) _selectedMarkerScreen.value = null
    }

    fun setMarkerScreen(point: Offset?) {
        _selectedMarkerScreen.value = point
    }

    /** Strava's Move Point: the next map tap relocates this stop. */
    fun beginMove(index: Int) {
        if (!isStop(index)) return
        _movingWaypoint.value = index
        _selectedWaypoint.value = index
    }

    fun cancelMove() {
        _movingWaypoint.value = null
        select(null)
    }

    /** The move index, consumed: null if no move was pending. */
    fun takeMove(): Int? = _movingWaypoint.value.also { cancelMove() }

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
