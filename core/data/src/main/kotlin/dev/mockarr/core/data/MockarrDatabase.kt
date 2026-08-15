package dev.mockarr.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SavedRouteEntity::class],
    version = 2,
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
    }
}
