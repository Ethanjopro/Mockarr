package dev.mockarr.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedRouteDao {

    @Query("SELECT * FROM saved_routes ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<SavedRouteEntity>>

    @Insert
    suspend fun insert(route: SavedRouteEntity): Long

    @Update
    suspend fun update(route: SavedRouteEntity)

    @Delete
    suspend fun delete(route: SavedRouteEntity)
}
