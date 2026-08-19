package dev.mockarr.app.ui.map

import dev.mockarr.core.model.LatLng

/** One-shot camera request; [seq] makes repeated identical requests distinct. */
sealed interface CameraCommand {
    val seq: Long

    /** Center on a point at a fixed zoom (locate, search). */
    data class Center(
        val target: LatLng,
        val zoom: Double,
        override val seq: Long,
    ) : CameraCommand

    /** Frame a whole route (saved-route load). */
    data class FitRoute(
        val points: List<LatLng>,
        override val seq: Long,
    ) : CameraCommand

    /**
     * Builder-mode reframe: no-op when [points] already fit the viewport,
     * otherwise pans/zooms OUT only — placing stops never zooms in.
     */
    data class EnsureVisible(
        val points: List<LatLng>,
        override val seq: Long,
    ) : CameraCommand
}
