package dev.opencab.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locomotive saved to the local roster.
 *
 * This table is scaffolded so later milestones can add roster editing without changing the
 * module boundaries.
 */
@Entity(tableName = "saved_loco")
data class SavedLocoEntity(
    @PrimaryKey val id: String,
    val name: String,
    val roadNumber: String,
    val createdAtEpochMillis: Long,
)
