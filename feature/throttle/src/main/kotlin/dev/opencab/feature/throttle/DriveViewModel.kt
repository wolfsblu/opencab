package dev.opencab.feature.throttle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.opencab.core.data.connection.ConnectionRepository
import dev.opencab.core.data.throttle.ThrottleSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Owns the drive screen's state and translates screen actions into engine intents.
 *
 * Composables never see a socket, a protocol command, or the engine itself; they render
 * [DriveUiState] and call [actions]. The app-scoped engine follows the connection lifecycle.
 */
@HiltViewModel
class DriveViewModel @Inject constructor(
    private val session: ThrottleSession,
    connectionRepository: ConnectionRepository,
) : ViewModel() {
    private val controller = session.controller

    val uiState =
        combine(controller.uiState, connectionRepository.state) { throttle, connection ->
            mapDriveUiState(throttle, connection)
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
