package dev.opencab.core.data.throttle

import dev.opencab.core.data.connection.DccClient
import dev.opencab.core.data.connection.DccConnectionState
import dev.opencab.core.data.roster.toRosterAddressOrNull
import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ServerRosterEntry
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import dev.opencab.core.protocol.session.LinkState
import dev.opencab.core.protocol.session.LocoStatus
import dev.opencab.core.protocol.session.SessionInput
import dev.opencab.core.protocol.session.SessionIntent
import dev.opencab.core.protocol.session.SessionReducer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/**
 * The multi-slot throttle domain engine.
 *
 * It owns the desired slot set, acquisition state, speed coalescing, function behaviour, and the
 * link lifecycle. The UI only sends [ThrottleIntent]s and renders [uiState]; it never touches a
 * socket or a protocol command.
 *
 * All intents and client events are serialized through one actor. Commands therefore reach the
 * transport in a single order, which is what makes stop and emergency stop reliably overtake a
 * pending coalesced speed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleController(
    internal val scope: CoroutineScope,
    internal val config: ThrottleConfig = ThrottleConfig(),
    internal val nowMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {
    internal val reducer = SessionReducer()
    internal val slots = LinkedHashMap<LocoAddress, ThrottleSlotState>()
    internal val pendingSpeeds = LinkedHashMap<LocoAddress, Int>()
    internal val momentaryJobs = LinkedHashMap<SlotFunction, Job>()
    internal val acquireJobs = LinkedHashMap<LocoAddress, Job>()

    internal var linkState: ThrottleLinkState = ThrottleLinkState.UNLINKED
    internal var linkError: ConnectionError? = null
    internal var reconnectAttempt: Int? = null
    internal var lastAlert: String? = null
    internal var currentClient: DccClient? = null
    internal var lastSpeedFlushAtMillis: Long? = null

    internal val inputs = Channel<ThrottleInput>(Channel.UNLIMITED)
    private val mutableUiState = MutableStateFlow(ThrottleUiState())
    val uiState: StateFlow<ThrottleUiState> = mutableUiState.asStateFlow()

    private var linkJobs: List<Job> = emptyList()
    private val actorJob: Job = scope.launch { runActor() }

    fun dispatch(intent: ThrottleIntent) {
        inputs.trySend(ThrottleInput.Intent(intent))
    }

    /** Stops the engine and releases its own coroutines. The transport is not closed. */
    fun close() {
        actorJob.cancel()
        detachLinkJobs()
        cancelTransientJobs()
        inputs.close()
    }

    internal suspend fun runActor() {
        while (currentCoroutineContext().isActive) {
            if (pendingSpeeds.isEmpty()) {
                val input = inputs.receiveCatching().getOrNull() ?: return
                handleInput(input)
                publish()
            } else if (!runSpeedCycle()) {
                return
            }
        }
    }

    private suspend fun runSpeedCycle(): Boolean {
        val wait = speedWriteWaitMillis()
        if (wait == 0L) {
            flushSpeeds()
            publish()
            return true
        }
        val keepGoing = select<Boolean> {
            inputs.onReceiveCatching { result ->
                val input = result.getOrNull()
                if (input == null) {
                    false
                } else {
                    handleInput(input)
                    true
                }
            }
            onTimeout(wait) {
                flushSpeeds()
                true
            }
        }
        if (keepGoing) publish()
        return keepGoing
    }

    private suspend fun handleInput(input: ThrottleInput) {
        when (input) {
            is ThrottleInput.Intent -> handleIntent(input.intent)
            is ThrottleInput.ClientState -> handleClientState(input.state)
            is ThrottleInput.ServerEvent -> handleServerEvent(input.event)
            is ThrottleInput.AcquireTimeout -> handleAcquireTimeout(input.address)
        }
    }

    private fun speedWriteWaitMillis(): Long {
        val last = lastSpeedFlushAtMillis ?: return 0
        return (last + config.speedWriteIntervalMillis - nowMillis()).coerceAtLeast(0)
    }

    internal fun publish() {
        mutableUiState.value = ThrottleUiState(
            link = linkState,
            reconnectAttempt = reconnectAttempt,
            linkError = linkError,
            trackPower = reducer.state.power,
            slots = slots.values.map { it.toUi(reducer.state, config) },
            serverRoster = reducer.state.roster.mapNotNull { entry ->
                val address = entry.address.toRosterAddressOrNull() ?: return@mapNotNull null
                ServerRosterEntry(id = entry.name, name = entry.name, address = address)
            },
            lastAlert = lastAlert,
        )
    }

    internal suspend fun handleLink(client: DccClient) {
        val hadLink = currentClient != null
        detachLinkJobs()
        if (hadLink) {
            transitionDown(ThrottleLinkState.DISCONNECTED, null, null)
        }
        currentClient = client
        linkState = ThrottleLinkState.DISCONNECTED
        linkError = null
        reconnectAttempt = null
        linkJobs = listOf(
            scope.launch {
                client.state.collect { inputs.send(ThrottleInput.ClientState(it)) }
            },
            scope.launch {
                client.events.collect { inputs.send(ThrottleInput.ServerEvent(it)) }
            },
        )
        inputs.trySend(ThrottleInput.ClientState(client.state.value))
        publish()
    }

    internal suspend fun handleUnlink() {
        detachLinkJobs()
        if (reducer.state.link == LinkState.CONNECTED) {
            reducer.handle(SessionInput.LinkDown(nowMillis()))
        }
        linkState = ThrottleLinkState.UNLINKED
        linkError = null
        reconnectAttempt = null
        lastSpeedFlushAtMillis = null
        pendingSpeeds.clear()
        cancelTransientJobs()
        slots.values.forEach {
            it.health = ThrottleHealth.OFFLINE
            it.reacquireStopPending = false
        }
        syncSlots()
        publish()
    }

    internal suspend fun handleClientState(state: DccConnectionState) {
        when (state) {
            is DccConnectionState.Connected -> {
                if (linkState != ThrottleLinkState.CONNECTED) {
                    transitionUp(state)
                }
            }

            is DccConnectionState.Reconnecting ->
                transitionDown(ThrottleLinkState.RECONNECTING, state.lastError, state.attempt)

            is DccConnectionState.Failed ->
                transitionDown(ThrottleLinkState.FAILED, state.error, null)

            is DccConnectionState.Connecting ->
                transitionDown(ThrottleLinkState.CONNECTING, null, null)

            DccConnectionState.Disconnected ->
                transitionDown(ThrottleLinkState.DISCONNECTED, null, null)
        }
        publish()
    }

    private suspend fun transitionDown(
        next: ThrottleLinkState,
        error: ConnectionError?,
        attempt: Int?,
    ) {
        if (reducer.state.link == LinkState.CONNECTED) {
            reducer.handle(SessionInput.LinkDown(nowMillis()))
        }
        linkState = next
        linkError = error
        reconnectAttempt = attempt
        lastSpeedFlushAtMillis = null
        pendingSpeeds.clear()
        cancelTransientJobs()
        slots.values.forEach {
            it.health = when {
                next == ThrottleLinkState.UNLINKED -> ThrottleHealth.OFFLINE
                it.desired -> ThrottleHealth.STALE
                else -> ThrottleHealth.OFFLINE
            }
            it.reacquireStopPending = false
        }
        syncSlots()
    }

    private suspend fun transitionUp(state: DccConnectionState.Connected) {
        linkState = ThrottleLinkState.CONNECTED
        linkError = null
        reconnectAttempt = null
        lastAlert = null
        lastSpeedFlushAtMillis = null
        pendingSpeeds.clear()
        cancelTransientJobs()
        reducer.handle(SessionInput.LinkUp(nowMillis()))

        // Reacquire must never resume the last commanded speed. The train may have been stopped
        // by the layout while the link was down, and a surprise restart is worse than a stop.
        slots.values.forEach {
            it.health = ThrottleHealth.ONLINE
            it.acquireFailed = false
            if (it.desired) {
                it.speed = 0.0
                it.emergencyStop = false
                it.reacquireStopPending = true
            }
        }

        val commands = reducer.handle(
            SessionInput.Event(WiThrottleEvent.Version(state.serverVersion), nowMillis()),
        )
        val sent = sendCommands(commands, null)
        if (!sent) markDesiredSlotsFailed()
        syncSlots()
        startTimeoutsForRequestedSlots()
    }

    internal suspend fun sendCommands(
        commands: List<WiThrottleCommand>,
        address: LocoAddress?,
    ): Boolean {
        val toSend = commands.filterNot { it.isTransportOwned() }
        if (toSend.isEmpty()) return true
        val client = currentClient ?: return false
        var allSent = true
        for (command in toSend) {
            val sent = try {
                client.send(command)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
            if (!sent) {
                allSent = false
                markCommandFailure(address)
            }
        }
        return allSent
    }

    private fun markCommandFailure(address: LocoAddress?) {
        val slot = address?.let { slots[it] } ?: return
        slot.issue = ThrottleIssue.CommandFailed(slot.address)
    }

    private fun markDesiredSlotsFailed() {
        slots.values.filter { it.desired }.forEach {
            it.acquireFailed = true
            it.acquisition = ThrottleAcquisitionState.FAILED
            it.issue = ThrottleIssue.CommandFailed(it.address)
        }
    }

    internal suspend fun flushSpeeds() {
        if (pendingSpeeds.isEmpty()) return
        val snapshot = pendingSpeeds.toMap()
        pendingSpeeds.clear()
        lastSpeedFlushAtMillis = nowMillis()
        for ((address, speed) in snapshot) {
            val slot = slots[address]
            if (slot != null && slot.canControl()) {
                val commands = reducer.applyIntent(SessionIntent.SetSpeed(address, speed), nowMillis())
                sendCommands(commands, address)
            }
        }
        syncSlots()
    }

    internal fun syncSlots() {
        val session = reducer.state
        val iterator = slots.values.iterator()
        while (iterator.hasNext()) {
            val slot = iterator.next()
            val loco = session.locos[slot.address]
            if (loco == null) {
                if (slot.desired) {
                    slot.acquisition = ThrottleAcquisitionState.LOST
                } else {
                    iterator.remove()
                }
                continue
            }
            if (slot.name == null) slot.name = loco.name
            slot.acquisition = when {
                slot.acquireFailed && loco.status == LocoStatus.REQUESTED ->
                    ThrottleAcquisitionState.FAILED

                else -> loco.status.toAcquisitionState()
            }
            if (loco.status == LocoStatus.STEAL_PENDING) {
                slot.acquireFailed = false
                slot.issue = ThrottleIssue.InUse(slot.address)
            }
            if (loco.status == LocoStatus.ACQUIRED) {
                slot.acquireFailed = false
                if (slot.issue is ThrottleIssue.InUse || slot.issue is ThrottleIssue.AcquireTimedOut) {
                    slot.issue = null
                }
                if (slot.address !in pendingSpeeds) {
                    slot.emergencyStop = loco.emergencyStop
                }
            }
            slot.health = when (linkState) {
                ThrottleLinkState.CONNECTED -> ThrottleHealth.ONLINE
                ThrottleLinkState.UNLINKED -> ThrottleHealth.OFFLINE
                else -> if (slot.desired) ThrottleHealth.STALE else ThrottleHealth.OFFLINE
            }
        }
    }

    internal fun cancelTransientJobs() {
        acquireJobs.values.toList().forEach { it.cancel() }
        acquireJobs.clear()
        momentaryJobs.values.toList().forEach { it.cancel() }
        momentaryJobs.clear()
    }

    private fun detachLinkJobs() {
        linkJobs.forEach { it.cancel() }
        linkJobs = emptyList()
        currentClient = null
    }

    private fun WiThrottleCommand.isTransportOwned(): Boolean = when (this) {
        is WiThrottleCommand.SetDeviceName, is WiThrottleCommand.SetHardwareId,
        WiThrottleCommand.Heartbeat, WiThrottleCommand.EnableHeartbeatMonitoring,
        WiThrottleCommand.DisableHeartbeatMonitoring, WiThrottleCommand.Quit,
        -> true

        else -> false
    }

    internal data class SlotFunction(
        val address: LocoAddress,
        val function: Int,
    )

    private companion object {
        const val NANOS_PER_MILLI: Long = 1_000_000
    }
}

/** Inputs are serialized by the single actor so client events and UI intents keep one order. */
internal sealed interface ThrottleInput {
    data class Intent(val intent: ThrottleIntent) : ThrottleInput

    data class ClientState(val state: DccConnectionState) : ThrottleInput

    data class ServerEvent(val event: WiThrottleEvent) : ThrottleInput

    data class AcquireTimeout(val address: LocoAddress) : ThrottleInput
}
