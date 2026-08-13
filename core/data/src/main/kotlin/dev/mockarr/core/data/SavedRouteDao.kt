package dev.mockarr.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedRouteDao {

    @Query("SELECT * FROM saved_routes ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<SavedRouteEntity>>

    @Insert
    suspend fun insert(route: SavedRouteEntity): Long

    @Delete
    suspend fun delete(route: SavedRouteEntity)
}
