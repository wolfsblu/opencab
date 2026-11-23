package dev.opencab.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RosterLocoDaoTest {
    private lateinit var database: OpenCabDatabase
    private lateinit var dao: RosterLocoDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OpenCabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.rosterLocoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertsAndObservesRosterEntries() = runBlocking {
        dao.insert(entity(id = "one", name = "Black Five"))
        dao.insert(entity(id = "two", name = "Flying Scotsman", address = 4472, type = "LONG"))

        val entries = dao.observeAll().first()

        assertEquals(listOf("Black Five", "Flying Scotsman"), entries.map { it.name })
        assertNotNull(dao.findById("one"))
    }

    @Test
    fun theUniqueAddressIndexRejectsADuplicate() = runBlocking {
        dao.insert(entity(id = "one"))

        val failure = runCatching { dao.insert(entity(id = "two")) }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun updatesFunctionLabelsAndLastUsedWithoutChangingOtherFields() = runBlocking {
        dao.insert(entity(id = "one"))

        dao.updateServerFunctionLabels("one", "0=Headlight")
        dao.markUsed("one", 9_999)

        val entry = dao.findById("one")
        assertEquals("0=Headlight", entry?.serverFunctionLabels)
        assertEquals(9_999L, entry?.lastUsedEpochMillis)
        assertEquals("Black Five", entry?.name)
    }

    @Test
    fun deletingRemovesTheEntry() = runBlocking {
        dao.insert(entity(id = "one"))

        dao.deleteById("one")

        assertNull(dao.findById("one"))
    }

    private fun entity(
        id: String,
        name: String = "Black Five",
        address: Int = 3,
        type: String = "SHORT",
    ): RosterLocoEntity = RosterLocoEntity(
        id = id,
        name = name,
        address = address,
        addressType = type,
        functionLabels = "",
        serverFunctionLabels = "",
        isFavorite = false,
        colorKey = "blue",
        iconKey = "steam",
        lastUsedEpochMillis = null,
        serverRosterId = null,
        createdAtEpochMillis = 1L,
    )
}
