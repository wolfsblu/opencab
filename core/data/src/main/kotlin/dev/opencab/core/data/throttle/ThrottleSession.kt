package dev.opencab.core.data.throttle

import dev.opencab.core.data.connection.ConnectionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped throttle engine bound to the live connection.
 *
 * The engine attaches to each [ConnectionRepository.activeClient] once. A reconnect keeps the
 * same client instance, so the engine stays attached and re-acquires desired slots itself; only a
 * new session (or a disconnect) detaches it. This is the single place where the UI boundary meets
 * the transport boundary for driving.
 */
@Singleton
class ThrottleSession @Inject constructor(
    connectionRepository: ConnectionRepository,
    ioDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    val controller: ThrottleController = ThrottleController(scope)

    init {
        scope.launch {
            connectionRepository.activeClient.collect { client ->
                if (client == null) {
                    controller.unlink()
                } else {
                    controller.link(client)
                }
            }
        }
    }

    /**
     * Single-slot convenience used by the Step 7 drive screen. Addresses stay inside the data
     * layer; Step 8's roster will call [controller] directly with an explicit address.
     */
    fun setSpeed(speed: Float) {
        primaryAddress()?.let { controller.setSpeed(it, speed.toDouble()) }
    }

    fun nudge(steps: Int) {
        primaryAddress()?.let { controller.nudge(it, steps) }
    }

    fun stop() {
        primaryAddress()?.let { controller.stop(it) }
    }

    fun setDirection(forward: Boolean) {
        primaryAddress()?.let { controller.setDirection(it, forward) }
    }

    fun toggleFunction(number: Int) {
        primaryAddress()?.let { controller.toggleFunction(it, number) }
    }

    fun setFunction(number: Int, on: Boolean) {
        primaryAddress()?.let { controller.setFunction(it, number, on) }
    }

    fun takeControl() {
        primaryAddress()?.let { controller.takeControl(it) }
    }

    fun retryAcquire() {
        val slot = controller.uiState.value.slots.firstOrNull() ?: return
        controller.acquire(slot.address, name = slot.name, speedSteps = slot.speedSteps)
    }

    fun release() {
        primaryAddress()?.let { controller.release(it) }
    }

    private fun primaryAddress() = controller.uiState.value.slots.firstOrNull()?.address
}
