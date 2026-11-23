package dev.opencab.feature.roster

import dev.opencab.core.data.throttle.SpeedSteps
import dev.opencab.core.data.throttle.ThrottleAcquisitionState
import dev.opencab.core.data.throttle.ThrottleHealth
import dev.opencab.core.data.throttle.ThrottleLinkState
import dev.opencab.core.data.throttle.ThrottleSlotUiState
import dev.opencab.core.data.throttle.ThrottleUiState
import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.model.RosterLoco
import dev.opencab.core.model.ServerRosterEntry
import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RosterViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val jinty = RosterLoco(
        id = "jinty",
        name = "Jinty",
        address = RosterAddress(3, RosterAddressType.SHORT),
        isFavorite = true,
    )
    private val blackFive = RosterLoco(
        id = "black-five",
        name = "Black Five",
        address = RosterAddress(3412, RosterAddressType.LONG),
    )
    private val imported = ServerRosterEntry(
        id = "layout-black-five",
        name = "Layout Black Five",
        address = RosterAddress(9, RosterAddressType.LONG),
    )

    @Test
    fun `the active address is marked in the list`() = runTest {
        val viewModel = viewModel(roster = listOf(jinty, blackFive), throttle = throttleWithActiveJinty())
        collectState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.locos.first { it.id == "jinty" }.isActive)
        assertFalse(state.locos.first { it.id == "black-five" }.isActive)
    }

    @Test
    fun `driving acquires the roster loco, marks it used, and opens drive`() = runTest {
        val repository = FakeRosterRepository(listOf(jinty))
        val acquirer = FakeLocoAcquirer()
        val viewModel = viewModel(repository = repository, acquirer = acquirer)
        collectState(viewModel)
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.onDrive(jinty.id)
        advanceUntilIdle()

        assertEquals(listOf(jinty), acquirer.acquired)
        assertEquals(listOf("jinty"), repository.markedUsed)
        assertEquals(listOf(RosterEvent.OpenDrive), events)
    }

    @Test
    fun `import asks the repository for the server roster`() = runTest {
        val repository = FakeRosterRepository(listOf(jinty))
        val viewModel = viewModel(
            repository = repository,
            throttle = FakeThrottleUiSource(
                ThrottleUiState(
                    link = ThrottleLinkState.CONNECTED,
                    serverRoster = listOf(imported),
                ),
            ),
        )
        collectState(viewModel)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.serverRosterCount)
        viewModel.onImportServerRoster()
        advanceUntilIdle()

        assertEquals(listOf(listOf(imported)), repository.imported)
        assertEquals(1, viewModel.uiState.value.importedCount)
        assertTrue(repository.current.any { it.serverRosterId == "layout-black-five" })
    }

    private fun throttleWithActiveJinty(): FakeThrottleUiSource = FakeThrottleUiSource(
        ThrottleUiState(
            link = ThrottleLinkState.CONNECTED,
            slots = listOf(slot(address = LocoAddress.short(3), name = "Jinty")),
        ),
    )

    private fun slot(
        address: LocoAddress,
        name: String,
    ): ThrottleSlotUiState = ThrottleSlotUiState(
        address = address,
        name = name,
        speedSteps = SpeedSteps.ONE_HUNDRED_TWENTY_EIGHT,
        speed = 0.0,
        direction = Direction.FORWARD,
        functions = emptyList(),
        acquisition = ThrottleAcquisitionState.ACQUIRED,
        health = ThrottleHealth.ONLINE,
        desired = true,
        emergencyStop = false,
    )

    private fun viewModel(
        roster: List<RosterLoco> = emptyList(),
        repository: FakeRosterRepository = FakeRosterRepository(roster),
        acquirer: FakeLocoAcquirer = FakeLocoAcquirer(),
        throttle: FakeThrottleUiSource = FakeThrottleUiSource(),
    ): RosterViewModel = RosterViewModel(repository, acquirer, throttle)

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: RosterViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    private fun kotlinx.coroutines.test.TestScope.collectEvents(
        viewModel: RosterViewModel,
    ): List<RosterEvent> {
        val events = mutableListOf<RosterEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventFlow.collect { events += it }
        }
        return events
    }
}
