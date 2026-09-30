package dev.mockarr.app.ui.screens

import dev.mockarr.core.data.RouteDraft

/** What of the map is worth keeping when the app closes; null when there are no stops to keep. */
internal fun MapViewModel.UiState.toDraft(): RouteDraft? =
    if (waypoints.isEmpty()) {
        null
    } else {
        RouteDraft(waypoints, profile, route, routedFor, routeSaved, routeIsFallback)
    }

/**
 * [draft] back on the map. A route drawn for other stops (an edit the app closed on before
 * its refetch) is dropped, and "saved" with it, so the caller fetches the stops afresh.
 */
internal fun MapViewModel.UiState.withDraft(draft: RouteDraft): MapViewModel.UiState {
    val current = draft.route != null && draft.routedFor == draft.waypoints.map { it.position }
    return copy(
        waypoints = draft.waypoints,
        profile = draft.profile,
        route = draft.route.takeIf { current },
        routedFor = draft.routedFor.takeIf { current },
        routeIsFallback = current && draft.routeIsFallback,
        routeSaved = current && draft.routeSaved,
    )
}
