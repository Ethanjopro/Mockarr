package dev.mockarr.app.ui.screens

import dev.mockarr.app.R
import dev.mockarr.core.model.RoutingProfile

/** Icon + short label for a travel mode, shared by the action row, the pickers and the strip. */
internal fun RoutingProfile.iconRes(): Int = when (this) {
    RoutingProfile.DRIVING -> R.drawable.ic_car
    RoutingProfile.WALKING -> R.drawable.ic_walk
    RoutingProfile.CYCLING -> R.drawable.ic_bike
}

internal fun RoutingProfile.shortLabelRes(): Int = when (this) {
    RoutingProfile.DRIVING -> R.string.row_mode_drive
    RoutingProfile.WALKING -> R.string.row_mode_walk
    RoutingProfile.CYCLING -> R.string.row_mode_cycle
}

/** The strip while playback moves: "Driving" / "Walking" / "Cycling". */
internal fun RoutingProfile.movingLabelRes(): Int = when (this) {
    RoutingProfile.DRIVING -> R.string.strip_driving
    RoutingProfile.WALKING -> R.string.strip_walking
    RoutingProfile.CYCLING -> R.string.strip_cycling
}

/** The strip once a route is built: "Ready to drive" / "Ready to walk" / "Ready to ride". */
internal fun RoutingProfile.readyLabelRes(): Int = when (this) {
    RoutingProfile.DRIVING -> R.string.strip_ready
    RoutingProfile.WALKING -> R.string.strip_ready_walk
    RoutingProfile.CYCLING -> R.string.strip_ready_cycle
}
