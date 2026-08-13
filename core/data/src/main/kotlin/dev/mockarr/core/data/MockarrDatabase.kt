package dev.mockarr.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SavedRouteEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MockarrDatabase : RoomDatabase() {
    abstract fun savedRouteDao(): SavedRouteDao

    companion object {
        const val NAME = "mockarr.db"
    }
}
