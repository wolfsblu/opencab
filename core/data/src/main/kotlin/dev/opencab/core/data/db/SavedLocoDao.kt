package dev.opencab.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocoDao {
    @Query("SELECT * FROM saved_loco ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SavedLocoEntity>>

    @Upsert
    suspend fun upsert(loco: SavedLocoEntity)

    @Query("DELETE FROM saved_loco WHERE id = :id")
    suspend fun deleteById(id: String)
}
