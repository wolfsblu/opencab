package dev.opencab.feature.roster

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocoEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val jinty = RosterLoco(
        id = "jinty",
        name = "Jinty",
        address = RosterAddress(3, RosterAddressType.SHORT),
    )

    @Test
    fun `an out of range short address is rejected before persistence`() = runTest {
        val repository = FakeRosterRepository()
        val viewModel = viewModel(repository)

        viewModel.onNameChange("Jinty")
        viewModel.onAddressChange("200")
        viewModel.onTypeChange(RosterAddressType.SHORT)
        viewModel.onSave()

        assertEquals(EditorSaveError.ADDRESS_INVALID, viewModel.uiState.value.saveError)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `a valid new loco saves and emits its new id`() = runTest {
        val repository = FakeRosterRepository()
        val viewModel = viewModel(repository)
        val events = collectEvents(viewModel)

        viewModel.onNameChange("Jinty")
        viewModel.onAddressChange("3")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(RosterAddress(3, RosterAddressType.SHORT), repository.created.single().address)
        val event = events.single() as EditorEvent.Saved
        assertEquals("created-1", event.locoId)
        assertTrue(event.createdNewEntry)
    }

    @Test
    fun `an untyped number in the overlap saves as short`() = runTest {
        val repository = FakeRosterRepository()
        val viewModel = viewModel(repository)

        viewModel.onNameChange("Overlap")
        viewModel.onAddressChange("42")
        viewModel.onSave()
        advanceUntilIdle()

        val draft = repository.created.single()
        assertEquals(RosterAddress(42, RosterAddressType.SHORT), draft.address)
    }

    @Test
    fun `a duplicate address is surfaced with the existing name`() = runTest {
        val repository = FakeRosterRepository(listOf(jinty))
        val viewModel = viewModel(repository)

        viewModel.onNameChange("Another")
        viewModel.onAddressChange("3")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals("Jinty", viewModel.uiState.value.duplicateName)
        assertEquals(EditorSaveError.ADDRESS_INVALID, viewModel.uiState.value.saveError)
        assertEquals(1, repository.current.size)

        viewModel.onAddressChange("4")

        assertNull(viewModel.uiState.value.duplicateName)
        assertNull(viewModel.uiState.value.saveError)
    }

    @Test
    fun `editing an existing loco updates in place`() = runTest {
        val repository = FakeRosterRepository(listOf(jinty))
        val viewModel = viewModel(repository, locoId = jinty.id)
        advanceUntilIdle()

        viewModel.onNameChange("Jinty (renamed)")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals("jinty", repository.updated.single().first)
        assertEquals(1, repository.current.size)
        assertEquals("Jinty (renamed)", repository.current.single().name)
        assertFalse(viewModel.uiState.value.addressChanged)
    }

    @Test
    fun `changing the address reports a new entry and keeps the old one`() = runTest {
        val repository = FakeRosterRepository(listOf(jinty))
        val viewModel = viewModel(repository, locoId = jinty.id)
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        viewModel.onAddressChange("3412")
        viewModel.onTypeChange(RosterAddressType.LONG)
        viewModel.onNameChange("Jinty")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.addressChanged)
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals("jinty", repository.updated.single().first)
        assertEquals(RosterAddress(3412, RosterAddressType.LONG), repository.updated.single().second.address)
        assertEquals(2, repository.current.size)
        val event = events.single() as EditorEvent.Saved
        assertTrue(event.createdNewEntry)
    }

    @Test
    fun `local function labels are saved with the draft`() = runTest {
        val repository = FakeRosterRepository()
        val viewModel = viewModel(repository)

        viewModel.onNameChange("Jinty")
        viewModel.onAddressChange("3")
        viewModel.onFunctionLabelChange(1, "Whistle")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals("Whistle", repository.created.single().localFunctionLabels[1])
    }

    @Test
    fun `a missing loco surfaces a not found error`() = runTest {
        val repository = FakeRosterRepository()
        val viewModel = viewModel(repository, locoId = "missing")
        advanceUntilIdle()

        assertEquals(EditorSaveError.NOT_FOUND, viewModel.uiState.value.saveError)
        assertNull(viewModel.uiState.value.duplicateName)
    }

    private fun viewModel(
        repository: FakeRosterRepository,
        locoId: String? = null,
    ): LocoEditorViewModel {
        val state = if (locoId == null) {
            SavedStateHandle()
        } else {
            SavedStateHandle(
                mapOf(ROSTER_LOCO_ID_ARG to locoId),
            )
        }
        return LocoEditorViewModel(repository, state)
    }

    private fun kotlinx.coroutines.test.TestScope.collectEvents(
        viewModel: LocoEditorViewModel,
    ): List<EditorEvent> {
        val events = mutableListOf<EditorEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.eventFlow.collect { events += it }
        }
        return events
    }
}
