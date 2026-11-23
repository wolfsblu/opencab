package dev.opencab.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RosterMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "roster-migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `migration carries a valid scaffold address into the roster and drops the old table`() {
        createVersionOneDatabase()

        val database = Room.databaseBuilder(context, OpenCabDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val sqlite = database.openHelper.readableDatabase

        val rows = sqlite.query(
            "SELECT id, name, address, addressType, isFavorite, colorKey, iconKey FROM roster_loco",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MigratedRow(
                            id = cursor.getString(0),
                            name = cursor.getString(1),
                            address = cursor.getInt(2),
                            addressType = cursor.getString(3),
                            isFavorite = cursor.getInt(4) != 0,
                            colorKey = cursor.getString(5),
                            iconKey = cursor.getString(6),
                        ),
                    )
                }
            }
        }

        assertEquals(1, rows.size)
        assertEquals("kept", rows.single().id)
        assertEquals("Black Five", rows.single().name)
        assertEquals(3412, rows.single().address)
        assertEquals("LONG", rows.single().addressType)
        assertFalse(rows.single().isFavorite)
        assertEquals("blue", rows.single().colorKey)
        assertEquals("steam", rows.single().iconKey)

        val tables = sqlite.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name",
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        assertTrue("roster_loco" in tables)
        assertFalse("saved_loco" in tables)
        database.close()
    }

    private fun createVersionOneDatabase() {
        context.deleteDatabase(databaseName)
        val legacy = SQLiteDatabase.openOrCreateDatabase(
            context.getDatabasePath(databaseName),
            null,
        )
        legacy.execSQL(
            """
            CREATE TABLE `saved_loco` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `roadNumber` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        legacy.execSQL("INSERT INTO saved_loco VALUES ('kept', 'Black Five', '3412', 10)")
        legacy.execSQL("INSERT INTO saved_loco VALUES ('dropped', 'Not a number', 'ABC', 11)")
        legacy.execSQL("INSERT INTO saved_loco VALUES ('zero', 'Zero', '0', 12)")
        legacy.version = 1
        legacy.close()
    }

    private data class MigratedRow(
        val id: String,
        val name: String,
        val address: Int,
        val addressType: String,
        val isFavorite: Boolean,
        val colorKey: String,
        val iconKey: String,
    )
}
