package dev.opencab.core.data.roster

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.opencab.core.data.db.OpenCabDatabase
import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.model.RosterColor
import dev.opencab.core.model.RosterIcon
import dev.opencab.core.model.RosterLocoDraft
import dev.opencab.core.model.ServerRosterEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DefaultRosterRepositoryTest {
    private lateinit var database: OpenCabDatabase
    private lateinit var repository: DefaultRosterRepository
    private var now: Long = 1_000L

    private val shortThree = RosterAddress(3, RosterAddressType.SHORT)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OpenCabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultRosterRepository(
            dao = database.rosterLocoDao(),
            clock = EpochClock { now },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `create persists every editable field`() = runBlocking {
        val result = repository.create(draft())

        val saved = (result as RosterSaveResult.Saved).loco
        assertTrue(result.createdNewEntry)
        assertEquals("Black Five", saved.name)
        assertEquals(shortThree, saved.address)
        assertTrue(saved.isFavorite)
        assertEquals(RosterColor.AMBER, saved.color)
        assertEquals(RosterIcon.DIESEL, saved.icon)
        assertEquals("Headlight", saved.localFunctionLabels[0])
        assertEquals(1_000L, saved.createdAtEpochMillis)
        assertNull(saved.lastUsedEpochMillis)
        assertEquals(saved, repository.loco(saved.id))
    }

    @Test
    fun `duplicate address is reported instead of inserting a second row`() = runBlocking {
        val first = repository.create(draft()) as RosterSaveResult.Saved

        val duplicate = repository.create(draft(name = "Another")) as RosterSaveResult.DuplicateAddress

        assertEquals(first.loco.id, duplicate.existing.id)
        assertEquals(1, repository.locos.first().size)
    }

    @Test
    fun `same address update mutates the existing entry`() = runBlocking {
        val saved = (repository.create(draft()) as RosterSaveResult.Saved).loco
        now = 2_000L

        val result = repository.update(saved.id, draft(name = "Renamed")) as RosterSaveResult.Saved

        assertFalse(result.createdNewEntry)
        assertEquals(saved.id, result.loco.id)
        assertEquals("Renamed", result.loco.name)
        assertEquals(1, repository.locos.first().size)
    }

    @Test
    fun `changing the address creates a new entry and leaves the old one intact`() = runBlocking {
        val original = (repository.create(draft()) as RosterSaveResult.Saved).loco
        repository.markUsed(original.id)
        now = 5_000L

        val result = repository.update(
            original.id,
            draft(address = RosterAddress(3412, RosterAddressType.LONG), name = "Black Five"),
        ) as RosterSaveResult.Saved

        assertTrue(result.createdNewEntry)
        assertNotEquals(original.id, result.loco.id)
        assertEquals(RosterAddress(3412, RosterAddressType.LONG), result.loco.address)
        assertEquals("Headlight", result.loco.localFunctionLabels[0])

        val all = repository.locos.first()
        assertEquals(2, all.size)
        val retained = repository.loco(original.id)!!
        assertEquals(original.id, retained.id)
        assertEquals(original.address, retained.address)
        assertEquals(original.name, retained.name)
        assertEquals(1_000L, retained.lastUsedEpochMillis)
    }

    @Test
    fun `changing the address clears the layout roster id on the new entry`() = runBlocking {
        val original = (
            repository.create(draft().copy(serverRosterId = "layout-1")) as RosterSaveResult.Saved
            ).loco

        val result = repository.update(
            original.id,
            draft(address = RosterAddress(3412, RosterAddressType.LONG)),
        ) as RosterSaveResult.Saved

        assertEquals("layout-1", repository.loco(original.id)?.serverRosterId)
        assertNull(result.loco.serverRosterId)
    }

    @Test
    fun `an update cannot steal another entry's address`() = runBlocking {
        val first = (repository.create(draft()) as RosterSaveResult.Saved).loco
        val second = (
            repository.create(draft(address = RosterAddress(4, RosterAddressType.SHORT), name = "Jinty"))
                as RosterSaveResult.Saved
            ).loco

        val result = repository.update(
            second.id,
            draft(address = first.address, name = "Jinty"),
        )

        val duplicate = result as RosterSaveResult.DuplicateAddress
        assertEquals(first.id, duplicate.existing.id)
        assertEquals(2, repository.locos.first().size)
    }

    @Test
    fun `last used is stored and server labels never overwrite local labels`() = runBlocking {
        val saved = (repository.create(draft()) as RosterSaveResult.Saved).loco
        now = 8_000L

        repository.markUsed(saved.id)
        repository.recordServerFunctionLabels(
            saved.address,
            mapOf(0 to "Lamp", 1 to "Horn", 2 to "Bell"),
        )

        val loaded = repository.loco(saved.id)!!
        assertEquals(8_000L, loaded.lastUsedEpochMillis)
        assertEquals("Headlight", loaded.functionLabel(0))
        assertEquals("Horn", loaded.functionLabel(1))
        assertEquals("Bell", loaded.functionLabel(2))
    }

    @Test
    fun `server roster import skips addresses already in the local roster`() = runBlocking {
        repository.create(draft())

        val added = repository.importServerRoster(
            listOf(
                ServerRosterEntry("local-name", "Layout Black Five", shortThree),
                ServerRosterEntry("E-3412", "Big Boy", RosterAddress(4014, RosterAddressType.LONG)),
            ),
        )

        assertEquals(1, added)
        val all = repository.locos.first()
        assertEquals(2, all.size)
        val imported = all.first { it.serverRosterId == "E-3412" }
        assertEquals("Big Boy", imported.name)
        assertEquals(RosterAddress(4014, RosterAddressType.LONG), imported.address)
    }

    @Test
    fun `a blank name is rejected`() = runBlocking {
        val result = repository.create(draft(name = "   "))

        assertEquals(RosterValidationError.NAME_REQUIRED, (result as RosterSaveResult.Invalid).reason)
        assertTrue(repository.locos.first().isEmpty())
    }

    private fun draft(
        name: String = "Black Five",
        address: RosterAddress = shortThree,
    ): RosterLocoDraft = RosterLocoDraft(
        name = name,
        address = address,
        localFunctionLabels = mapOf(0 to "Headlight"),
        isFavorite = true,
        color = RosterColor.AMBER,
        icon = RosterIcon.DIESEL,
    )
}
