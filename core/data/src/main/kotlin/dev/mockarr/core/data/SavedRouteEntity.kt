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

    /** JSON `List<String?>` of stop names, aligned with [waypointsJson]; null on pre-v5 rows. */
    val waypointNamesJson: String? = null,

    /**
     * Where the route is (its city), looked up when it was saved — its own field, so a
     * comma in a name the user typed is never read as a place. Pre-v6 rows were backfilled
     * from the text after their name's last comma. Null: not known.
     */
    val place: String? = null,
)

/**
 * The place a pre-v6 name carries after its last ", " ("Reunion Tower to Main Street,
 * Dallas"), or null. Only for backfilling old rows; new rows store [SavedRouteEntity.place].
 */
fun legacyPlaceOf(name: String): String? {
    val index = name.lastIndexOf(", ")
    if (index <= 0) return null
    return name.substring(index + 2).trim().takeIf { it.isNotEmpty() }
}
