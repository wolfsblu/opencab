package dev.opencab.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RosterLocoEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class OpenCabDatabase : RoomDatabase() {
    abstract fun rosterLocoDao(): RosterLocoDao

    companion object {
        const val NAME: String = "opencab.db"
    }
}
