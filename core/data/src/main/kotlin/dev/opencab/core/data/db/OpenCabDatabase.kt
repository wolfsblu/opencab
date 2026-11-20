package dev.opencab.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SavedLocoEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OpenCabDatabase : RoomDatabase() {
    abstract fun savedLocoDao(): SavedLocoDao

    companion object {
        const val NAME: String = "opencab.db"
    }
}
