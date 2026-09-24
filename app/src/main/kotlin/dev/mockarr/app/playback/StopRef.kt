package dev.mockarr.app.playback

import android.content.res.Resources
import dev.mockarr.app.R
import dev.mockarr.core.model.PlaybackState

/** A stop the drive has reached, as the user knows it — the band and the notification word it. */
internal sealed interface StopRef {
    /** The stop's own name ("Reunion Tower"). */
    data class Named(val name: String) : StopRef

    /** An unnamed final stop. */
    data object Destination : StopRef

    /** An unnamed stop by its number in the user's route — never the drive-in's shifted count. */
    data class Numbered(val number: Int) : StopRef
}

/** Driven waypoint [index] of this drive: its name, else "destination" / "stop N" of the planned route. */
internal fun MockSessionRepository.LiveDrive?.stopRef(index: Int, isDestination: Boolean): StopRef {
    val name = this?.route?.waypointNames?.getOrNull(index)
    return when {
        name != null -> StopRef.Named(name)
        isDestination -> StopRef.Destination
        else -> StopRef.Numbered((this?.plannedIndex(index) ?: index) + 1)
    }
}

/** The notification's title at a stop ("Waiting at Reunion Tower"), or null while moving or paused. */
internal fun waitingTitle(res: Resources, state: PlaybackState?, drive: MockSessionRepository.LiveDrive?): String? {
    val dwelling = (state as? PlaybackState.Dwelling)?.takeIf { it.waypointIndex >= 0 } ?: return null
    return when (val stop = drive.stopRef(dwelling.waypointIndex, dwelling.isDestination)) {
        is StopRef.Named -> res.getString(R.string.strip_waiting_at_spoken, stop.name)
        StopRef.Destination -> res.getString(R.string.strip_waiting_destination_spoken)
        is StopRef.Numbered -> res.getString(R.string.strip_waiting_spoken, stop.number)
    }
}
