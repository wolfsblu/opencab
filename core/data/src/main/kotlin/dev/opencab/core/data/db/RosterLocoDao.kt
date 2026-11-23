package dev.opencab.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RosterLocoDao {
    @Query("SELECT * FROM roster_loco ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RosterLocoEntity>>

    @Query("SELECT * FROM roster_loco WHERE id = :id")
    fun observeById(id: String): Flow<RosterLocoEntity?>

    @Query("SELECT * FROM roster_loco WHERE id = :id")
    suspend fun findById(id: String): RosterLocoEntity?

    @Query("SELECT * FROM roster_loco WHERE address = :address AND addressType = :addressType LIMIT 1")
    suspend fun findByAddress(
        address: Int,
        addressType: String,
    ): RosterLocoEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(loco: RosterLocoEntity)

    @Update
    suspend fun update(loco: RosterLocoEntity)

    @Query("UPDATE roster_loco SET lastUsedEpochMillis = :atEpochMillis WHERE id = :id")
    suspend fun markUsed(
        id: String,
        atEpochMillis: Long,
    )

    @Query("UPDATE roster_loco SET serverFunctionLabels = :encodedLabels WHERE id = :id")
    suspend fun updateServerFunctionLabels(
        id: String,
        encodedLabels: String,
    )

    @Query("DELETE FROM roster_loco WHERE id = :id")
    suspend fun deleteById(id: String)
}
