package dev.opencab.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One locomotive in the persisted roster.
 *
 * Function labels are stored in two columns: labels the operator edited locally and labels the
 * layout sent for the acquired locomotive. Keeping them separate lets a local label override the
 * server label without destroying the server's copy when the override is cleared.
 *
 * The unique index on address and type is the database-level guard against duplicate roster
 * entries; the repository reports the clash as a typed result rather than letting SQLite throw.
 */
@Entity(
    tableName = "roster_loco",
    indices = [
        Index(
            value = ["address", "addressType"],
            unique = true,
            name = "index_roster_loco_address_addressType",
        ),
    ],
)
data class RosterLocoEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: Int,
    val addressType: String,
    val functionLabels: String,
    val serverFunctionLabels: String,
    val isFavorite: Boolean,
    val colorKey: String,
    val iconKey: String,
    val lastUsedEpochMillis: Long?,
    val serverRosterId: String?,
    val createdAtEpochMillis: Long,
)
