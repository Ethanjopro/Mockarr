package dev.mockarr.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved route: geometry as encoded polyline6 + per-segment timing as JSON,
 * so replay needs no network and migrations stay trivial.
 */
@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtEpochMillis: Long,
    val profile: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val encodedPolyline6: String,
    val legsJson: String,
    val altitudesJson: String? = null,
    /** JSON `List<LatLng>` of router-snapped waypoints; null on pre-v3 rows. */
    val waypointsJson: String? = null,
    /** JSON `List<Int>` of per-waypoint wait seconds, aligned with [waypointsJson]. */
    val waypointWaitsJson: String? = null,

    /** JSON `List<OffRoadSpan>` of dotted-connector point ranges; null on pre-v4 rows. */
    val offRoadSpansJson: String? = null,
)
