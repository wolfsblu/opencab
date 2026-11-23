package dev.opencab.feature.roster

import dev.opencab.core.data.throttle.PrimaryAcquireResult
import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.model.RosterLoco
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
class LocoPickerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val blackFive = RosterLoco(
        id = "black-five",
        name = "Black Five",
        address = RosterAddress(3412, RosterAddressType.LONG),
        isFavorite = true,
    )
    private val jinty = RosterLoco(
        id = "jinty",
        name = "Jinty",
        address = RosterAddress(3, RosterAddressType.SHORT),
        lastUsedEpochMillis = 5_000L,
    )

    @Test
    fun `search filters the picker by name`() = runTest {
        val viewModel = viewModel(listOf(blackFive, jinty))
        collectState(viewModel)

        viewModel.onQueryChange("jint")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.sections.size)
        assertEquals(PickerSectionKind.Matches, state.sections.single().kind)
        assertEquals(listOf(jinty), state.sections.single().locos)
    }

    @Test
    fun `empty roster opens the add form and the first save acquires`() = runTest {
        val repository = FakeRosterRepository()
        val acquirer = FakeLocoAcquirer()
        val viewModel = viewModel(repository = repository, acquirer = acquirer)
        collectState(viewModel)
        val results = collectResults(viewModel)

        assertTrue(viewModel.uiState.value.isEmpty)
        viewModel.onStartAdd()
        viewModel.onAddNameChange("Jinty")
        viewModel.onAddAddressChange("3")
        viewModel.onSubmitAdd()
        advanceUntilIdle()

        assertEquals(PickerMode.AddAddress, viewModel.uiState.value.mode)
        assertEquals(1, repository.created.size)
        assertEquals(RosterAddress(3, RosterAddressType.SHORT), repository.created.single().address)
        assertEquals(listOf("3"), acquirer.acquired.map { it.address.number.toString() })
        assertEquals(listOf("created-1"), repository.markedUsed)
        assertEquals("created-1", results.single().locoId)
    }

    @Test
    fun `a duplicate add is reported and does not acquire`() = runTest {
        val repository = FakeRosterRepository(listOf(jinty))
        val acquirer = FakeLocoAcquirer()
        val viewModel = viewModel(repository = repository, acquirer = acquirer)
        collectState(viewModel)
        val results = collectResults(viewModel)

        viewModel.onStartAdd()
        viewModel.onAddAddressChange("3")
        viewModel.onSubmitAdd()
        advanceUntilIdle()

        val error = viewModel.uiState.value.addError as PickerAddError.Duplicate
        assertEquals(jinty, error.existing)
        assertTrue(acquirer.acquired.isEmpty())
        assertTrue(results.isEmpty())
        assertEquals(1, repository.current.size)
    }

    @Test
    fun `using the existing entry from a duplicate acquires that entry`() = runTest {
        val repository = FakeRosterRepository(listOf(jinty))
        val acquirer = FakeLocoAcquirer()
        val viewModel = viewModel(repository = repository, acquirer = acquirer)
        collectState(viewModel)
        val results = collectResults(viewModel)

        viewModel.onStartAdd()
        viewModel.onAddAddressChange("3")
        viewModel.onSubmitAdd()
        advanceUntilIdle()
        viewModel.onUseExisting()
        advanceUntilIdle()

        assertEquals(listOf(jinty), acquirer.acquired)
        assertEquals(listOf("jinty"), repository.markedUsed)
        assertEquals("jinty", results.single().locoId)
    }

    @Test
    fun `an invalid short address stays in the form`() = runTest {
        val repository = FakeRosterRepository()
        val acquirer = FakeLocoAcquirer()
        val viewModel = viewModel(repository = repository, acquirer = acquirer)
        collectState(viewModel)

        viewModel.onStartAdd()
        viewModel.onAddAddressChange("200")
        viewModel.onAddTypeChange(RosterAddressType.SHORT)
        viewModel.onSubmitAdd()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.addError is PickerAddError.InvalidAddress)
        assertTrue(repository.created.isEmpty())
        assertTrue(acquirer.acquired.isEmpty())
    }

    @Test
    fun `selecting a loco marks it used and closes with an already active flag`() = runTest {
        val repository = FakeRosterRepository(listOf(jinty))
        val acquirer = FakeLocoAcquirer().apply {
            result = PrimaryAcquireResult.ALREADY_ACTIVE
        }
        val viewModel = viewModel(repository = repository, acquirer = acquirer)
        collectState(viewModel)
        val results = collectResults(viewModel)

        viewModel.onSelect(jinty)
        advanceUntilIdle()

        assertTrue(results.single().wasAlreadyActive)
        assertEquals(listOf("jinty"), repository.markedUsed)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    private fun viewModel(
        locos: List<RosterLoco> = emptyList(),
        repository: FakeRosterRepository = FakeRosterRepository(locos),
        acquirer: FakeLocoAcquirer = FakeLocoAcquirer(),
        throttle: FakeThrottleUiSource = FakeThrottleUiSource(),
    ): LocoPickerViewModel = LocoPickerViewModel(repository, acquirer, throttle)

    private fun kotlinx.coroutines.test.TestScope.collectState(viewModel: LocoPickerViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    private fun kotlinx.coroutines.test.TestScope.collectResults(
        viewModel: LocoPickerViewModel,
    ): List<PickerResult> {
        val results = mutableListOf<PickerResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventFlow.collect { results += it }
        }
        return results
    }
}
