package dev.mockarr.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SavedRouteEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class MockarrDatabase : RoomDatabase() {
    abstract fun savedRouteDao(): SavedRouteDao

    companion object {
        const val NAME = "mockarr.db"

        /** v2: terrain elevation profile for saved routes (nullable — old rows stay valid). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_routes ADD COLUMN altitudesJson TEXT")
            }
        }

        /** v3: waypoints + per-waypoint wait times (nullable — old rows stay valid). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_routes ADD COLUMN waypointsJson TEXT")
                db.execSQL("ALTER TABLE saved_routes ADD COLUMN waypointWaitsJson TEXT")
            }
        }

        /** v4: off-road connector spans (nullable — old rows replay without them). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_routes ADD COLUMN offRoadSpansJson TEXT")
            }
        }
    }
}
