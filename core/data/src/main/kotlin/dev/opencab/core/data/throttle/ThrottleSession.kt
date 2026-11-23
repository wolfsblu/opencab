package dev.opencab.core.data.throttle

import dev.opencab.core.data.connection.ConnectionRepository
import dev.opencab.core.data.roster.toLocoAddress
import dev.opencab.core.data.roster.toRosterAddressOrNull
import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterLoco
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What happened when the roster asked to drive a locomotive. */
enum class PrimaryAcquireResult {
    /** The same address was already requested or acquired; no duplicate slot was created. */
    ALREADY_ACTIVE,

    /** A fresh acquisition was sent to the layout. */
    REQUESTED,
}

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
) : ThrottleUiSource {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    val controller: ThrottleController = ThrottleController(scope)

    /** Read-only engine snapshot for roster and picker surfaces. */
    override val uiState: StateFlow<ThrottleUiState>
        get() = controller.uiState

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
     * Single-slot convenience used by the Drive screen. Addresses stay inside the data layer;
     * roster-driven acquisition goes through [acquirePrimary] with an explicit roster entry.
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
        val slots = controller.uiState.value.slots
        val slot = slots.lastOrNull { it.desired } ?: slots.lastOrNull() ?: return
        controller.acquire(slot.address, name = slot.name, speedSteps = slot.speedSteps)
    }

    fun release() {
        primaryAddress()?.let { controller.release(it) }
    }

    /**
     * Drives one roster locomotive and releases any other desired slot first.
     *
     * v1 exposes one active locomotive. Repeating the same selection is a no-op rather than a
     * second acquisition, so the engine never carries two slots for one address.
     */
    fun acquirePrimary(loco: RosterLoco): PrimaryAcquireResult {
        val target = loco.address.toLocoAddress()
        val snapshot = controller.uiState.value
        val existing = snapshot.slots.firstOrNull { it.address == target }
        if (existing != null && existing.desired && existing.acquisition in ACTIVE_ACQUISITIONS) {
            return PrimaryAcquireResult.ALREADY_ACTIVE
        }
        snapshot.slots
            .filter { it.desired && it.address != target }
            .forEach { controller.release(it.address) }
        controller.acquire(
            address = target,
            name = loco.displayName,
            rosterEntryId = loco.serverRosterId,
        )
        return PrimaryAcquireResult.REQUESTED
    }

    /** The address of the locomotive the operator is currently driving, if any. */
    fun activeAddress(): RosterAddress? = controller.uiState.value.slots
        .firstOrNull { it.desired }
        ?.address
        ?.toRosterAddressOrNull()

    private fun primaryAddress() = controller.uiState.value.slots.lastOrNull { it.desired }?.address
}

private val ACTIVE_ACQUISITIONS: Set<ThrottleAcquisitionState> = setOf(
    ThrottleAcquisitionState.REQUESTED,
    ThrottleAcquisitionState.ACQUIRED,
    ThrottleAcquisitionState.IN_USE,
    ThrottleAcquisitionState.TAKING_CONTROL,
)
