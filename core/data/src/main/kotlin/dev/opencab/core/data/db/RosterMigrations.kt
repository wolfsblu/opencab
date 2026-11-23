package dev.opencab.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Roster v1 was an unused scaffold (`saved_loco`: id, name, roadNumber, createdAt).
 *
 * The migration creates the real roster table, carries forward any row whose road number is a
 * valid DCC address, and drops the scaffold. Invalid or duplicate scaffold rows cannot be given
 * a meaningful address, so they are discarded; the scaffold was never exposed in the UI.
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `roster_loco` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `address` INTEGER NOT NULL,
                `addressType` TEXT NOT NULL,
                `functionLabels` TEXT NOT NULL,
                `serverFunctionLabels` TEXT NOT NULL,
                `isFavorite` INTEGER NOT NULL,
                `colorKey` TEXT NOT NULL,
                `iconKey` TEXT NOT NULL,
                `lastUsedEpochMillis` INTEGER,
                `serverRosterId` TEXT,
                `createdAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_roster_loco_address_addressType`
            ON `roster_loco` (`address`, `addressType`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `roster_loco` (
                `id`, `name`, `address`, `addressType`, `functionLabels`,
                `serverFunctionLabels`, `isFavorite`, `colorKey`, `iconKey`,
                `lastUsedEpochMillis`, `serverRosterId`, `createdAtEpochMillis`
            )
            SELECT
                `id`,
                `name`,
                CAST(`roadNumber` AS INTEGER),
                CASE WHEN CAST(`roadNumber` AS INTEGER) <= 127 THEN 'SHORT' ELSE 'LONG' END,
                '',
                '',
                0,
                'blue',
                'steam',
                NULL,
                NULL,
                `createdAtEpochMillis`
            FROM `saved_loco`
            WHERE `roadNumber` NOT GLOB '*[^0-9]*'
              AND CAST(`roadNumber` AS INTEGER) BETWEEN 1 AND 10239
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE IF EXISTS `saved_loco`")
    }
}
