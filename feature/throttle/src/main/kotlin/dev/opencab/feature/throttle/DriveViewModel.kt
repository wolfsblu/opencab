package dev.opencab.feature.throttle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.opencab.core.data.connection.ConnectionRepository
import dev.opencab.core.data.roster.RosterRepository
import dev.opencab.core.data.roster.toRosterAddressOrNull
import dev.opencab.core.data.throttle.ThrottleSession
import dev.opencab.core.data.throttle.ThrottleSlotUiState
import dev.opencab.core.data.throttle.ThrottleUiState
import dev.opencab.core.model.RosterLoco
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the drive screen's state and translates screen actions into engine intents.
 *
 * Composables never see a socket, a protocol command, or the engine itself; they render
 * [DriveUiState] and call [actions]. The app-scoped engine follows the connection lifecycle.
 *
 * The roster supplies local function labels for the acquired address. A local label always wins
 * over the layout's label. Labels sent by the layout are copied back to the roster so the editor
 * can show them as fallbacks without overwriting the operator's text.
 */
@HiltViewModel
class DriveViewModel @Inject constructor(
    private val session: ThrottleSession,
    connectionRepository: ConnectionRepository,
    private val rosterRepository: RosterRepository,
) : ViewModel() {
    private val controller = session.controller

    init {
        viewModelScope.launch {
            controller.uiState
                .mapNotNull { it.primarySlot()?.let { slot -> slot to slot.serverLabels() } }
                .distinctUntilChangedBy { (slot, labels) -> slot.addressKey to labels }
                .collect { (slot, labels) ->
                    val address = slot.toRosterAddressOrNull() ?: return@collect
                    rosterRepository.recordServerFunctionLabels(address, labels)
                }
        }
    }

    val uiState =
        combine(
            controller.uiState,
            connectionRepository.state,
            rosterRepository.locos,
        ) { throttle, connection, roster ->
            mapDriveUiState(
                throttle = throttle,
                connection = connection,
                localFunctionLabels = roster.localLabelsFor(throttle),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = mapDriveUiState(
                throttle = controller.uiState.value,
                connection = connectionRepository.state.value,
            ),
        )

    val actions: DriveActions = object : DriveActions {
        override fun onSpeedChanged(speed: Float) {
            session.setSpeed(speed)
        }

        override fun onNudge(steps: Int) {
            session.nudge(steps)
        }

        override fun onStop() {
            session.stop()
        }

        override fun onDirectionChange(direction: DriveDirection) {
            session.setDirection(forward = direction == DriveDirection.Forward)
        }

        override fun onToggleFunction(number: Int) {
            session.toggleFunction(number)
        }

        override fun onFunctionHoldStart(number: Int) {
            session.setFunction(number, on = true)
        }

        override fun onFunctionHoldEnd(number: Int) {
            session.setFunction(number, on = false)
        }

        override fun onTakeControl() {
            session.takeControl()
        }

        override fun onRetryAcquire() {
            session.retryAcquire()
        }

        override fun onRelease() {
            session.release()
        }
    }
}

private fun ThrottleUiState.primarySlot(): ThrottleSlotUiState? = slots.lastOrNull { it.desired } ?: slots.lastOrNull()

private fun ThrottleSlotUiState.serverLabels(): Map<Int, String> = functions
    .mapNotNull { function ->
        function.label.takeIf { it.isNotBlank() && it != "F${function.number}" }
            ?.let { function.number to it }
    }
    .toMap()

private fun List<RosterLoco>.localLabelsFor(throttle: ThrottleUiState): Map<Int, String> {
    val slot = throttle.primarySlot() ?: return emptyMap()
    val entry = firstOrNull { it.address.wireText == slot.addressKey } ?: return emptyMap()
    return entry.localFunctionLabels
}
