package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.LocoSelection
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent

/**
 * Pure WiThrottle session state machine.
 *
 * It owns the handshake, heartbeat scheduling policy, track-power state, and per-loco acquisition
 * state. It never opens sockets, starts threads, reads a clock, or uses coroutines with real time.
 * Every input carries the caller's monotonic timestamp; [handle] returns the commands to send.
 */
class SessionReducer(
    internal val config: SessionConfig = SessionConfig(),
) {
    var state: SessionState = SessionState()
        internal set

    fun handle(input: SessionInput): List<WiThrottleCommand> = when (input) {
        is SessionInput.LinkUp -> onLinkUp(input.atMillis)
        is SessionInput.LinkDown -> onLinkDown()
        is SessionInput.Tick -> onTick(input.atMillis)
        is SessionInput.Event -> onEvent(input.event, input.atMillis)
        is SessionInput.Intent -> onIntent(input.intent, input.atMillis)
    }

    internal fun emit(
        commands: List<WiThrottleCommand>,
        nowMillis: Long,
    ): List<WiThrottleCommand> {
        if (commands.isEmpty()) return commands
        state = state.copy(lastOutboundAtMillis = nowMillis)
        if (commands.any { it is WiThrottleCommand.Heartbeat }) {
            state = state.copy(heartbeat = state.heartbeat.copy(lastSentAtMillis = nowMillis))
        }
        return commands
    }

    internal fun updateLoco(
        address: LocoAddress,
        transform: (LocoSlot) -> LocoSlot,
    ) {
        val slot = state.locos[address] ?: return
        state = state.copy(locos = state.locos + (address to transform(slot)))
    }

    internal fun applyTo(
        locoId: LocoId,
        transform: (LocoSlot) -> LocoSlot,
    ) {
        when (locoId) {
            is LocoId.All ->
                state =
                    state.copy(locos = state.locos.mapValues { (_, slot) -> transform(slot) })

            is LocoId.Addressed -> updateLoco(locoId.address, transform)
        }
    }

    internal fun applyFunctionTo(
        locoId: LocoId,
        number: Int,
        transform: (FunctionSlot) -> FunctionSlot,
    ) {
        when (locoId) {
            is LocoId.All ->
                state =
                    state.copy(
                        locos =
                        state.locos.mapValues { (_, slot) ->
                            slot.withFunction(number, transform(slot.function(number)))
                        },
                    )

            is LocoId.Addressed ->
                updateLoco(locoId.address) { slot ->
                    slot.withFunction(number, transform(slot.function(number)))
                }
        }
    }

    internal fun controllableSlot(address: LocoAddress): LocoSlot? {
        val slot = state.locos[address] ?: return null
        return slot.takeIf { state.canControl && slot.status == LocoStatus.ACQUIRED }
    }

    internal fun rosterName(address: LocoAddress): String? = state.roster.firstOrNull { it.address == address }?.name

    internal fun acquireCommand(
        address: LocoAddress,
        rosterEntryId: String? = null,
    ): WiThrottleCommand.Acquire = WiThrottleCommand.Acquire(
        config.throttleKey,
        address,
        rosterEntryId?.let(LocoSelection::RosterEntry) ?: LocoSelection.Address(address),
    )

    private fun onLinkUp(nowMillis: Long): List<WiThrottleCommand> {
        if (state.link == LinkState.CONNECTED) return emptyList()
        state =
            state.copy(
                link = LinkState.CONNECTED,
                handshake = HandshakeState.AwaitingGreeting,
                server = ServerInfo(),
                heartbeat = HeartbeatState(),
                lastAlert = null,
                lastInfo = null,
            )
        return emit(
            listOf(
                WiThrottleCommand.SetHardwareId(config.hardwareId),
                WiThrottleCommand.SetDeviceName(config.deviceName),
            ),
            nowMillis,
        )
    }

    private fun onLinkDown(): List<WiThrottleCommand> {
        if (state.link == LinkState.DISCONNECTED) return emptyList()
        state =
            state.copy(
                link = LinkState.DISCONNECTED,
                handshake = HandshakeState.Idle,
                heartbeat = HeartbeatState(),
                lastOutboundAtMillis = null,
                locos = state.locos.mapValues { (_, slot) -> slot.disconnected() },
            )
        return emptyList()
    }

    private fun onTick(nowMillis: Long): List<WiThrottleCommand> {
        if (!isHeartbeatDue(nowMillis)) return emptyList()
        return emit(listOf(WiThrottleCommand.Heartbeat), nowMillis)
    }

    private fun isHeartbeatDue(nowMillis: Long): Boolean {
        if (state.link != LinkState.CONNECTED) return false
        val interval = state.heartbeat.intervalSeconds ?: return false
        if (interval <= 0 || !state.heartbeat.monitoringEnabled) return false
        val last = state.lastOutboundAtMillis
        return last == null || nowMillis - last >= config.heartbeat.sendIntervalMillis(interval)
    }

    private fun onEvent(
        event: WiThrottleEvent,
        nowMillis: Long,
    ): List<WiThrottleCommand> = when (event) {
        is WiThrottleEvent.Version, is WiThrottleEvent.HeartbeatInterval ->
            handleHandshakeEvent(event, nowMillis)

        is WiThrottleEvent.TrackPower, is WiThrottleEvent.ServerType, is WiThrottleEvent.ServerDescription,
        is WiThrottleEvent.Alert, is WiThrottleEvent.Info, is WiThrottleEvent.WebPort,
        is WiThrottleEvent.RosterList, is WiThrottleEvent.ConsistListHeader, is WiThrottleEvent.ConsistEntry,
        is WiThrottleEvent.FastClock,
        -> handleSystemEvent(event)

        is WiThrottleEvent.LocoAdded, is WiThrottleEvent.LocoRemoved, is WiThrottleEvent.StealRequested,
        is WiThrottleEvent.FunctionLabels,
        -> handleAcquisitionEvent(event)

        is WiThrottleEvent.SpeedChanged, is WiThrottleEvent.DirectionChanged,
        is WiThrottleEvent.SpeedStepModeChanged, is WiThrottleEvent.FunctionStateChanged,
        is WiThrottleEvent.FunctionMomentaryChanged,
        -> handleLocoStateEvent(event)

        is WiThrottleEvent.Unknown -> onUnknown()
    }

    private fun onIntent(
        intent: SessionIntent,
        nowMillis: Long,
    ): List<WiThrottleCommand> = when (intent) {
        is SessionIntent.Acquire, is SessionIntent.Release, is SessionIntent.ConfirmSteal ->
            handleAcquisitionIntent(intent, nowMillis)

        is SessionIntent.SetSpeed, is SessionIntent.SetDirection, is SessionIntent.EmergencyStop,
        is SessionIntent.SetSpeedStepMode, is SessionIntent.QuerySpeed, is SessionIntent.QueryDirection,
        is SessionIntent.QuerySpeedStepMode, is SessionIntent.QueryMomentaryStates,
        ->
            handleMotionIntent(intent, nowMillis)

        is SessionIntent.PressFunction, is SessionIntent.ReleaseFunction, is SessionIntent.ForceFunction,
        is SessionIntent.SetFunctionMomentary,
        -> handleFunctionIntent(intent, nowMillis)

        is SessionIntent.SetConsistLead, is SessionIntent.AddToConsist, is SessionIntent.RemoveFromConsist,
        is SessionIntent.SetConsistOrder, is SessionIntent.DeleteConsist,
        ->
            handleConsistIntent(intent, nowMillis)

        is SessionIntent.SetTrackPower, is SessionIntent.SetHeartbeatEnabled, SessionIntent.Quit ->
            handleSystemIntent(intent, nowMillis)
    }

    private fun onUnknown(): List<WiThrottleCommand> {
        state = state.copy(unknownMessageCount = state.unknownMessageCount + 1)
        return emptyList()
    }
}

private fun LocoSlot.withFunction(
    number: Int,
    value: FunctionSlot,
): LocoSlot = copy(functions = functions + (number to value))

private fun LocoSlot.disconnected(): LocoSlot {
    val nextStatus =
        when (status) {
            LocoStatus.ACQUIRED, LocoStatus.REQUESTED, LocoStatus.STEAL_PENDING,
            LocoStatus.STEALING, LocoStatus.RELEASING,
            -> if (desired) LocoStatus.LOST else LocoStatus.IDLE

            LocoStatus.IDLE, LocoStatus.LOST -> status
        }
    val nextSource =
        when (speedSource) {
            SpeedSource.LIVE, SpeedSource.COMMANDED -> SpeedSource.LAST_KNOWN
            SpeedSource.UNKNOWN, SpeedSource.LAST_KNOWN -> speedSource
        }
    return copy(status = nextStatus, speedSource = nextSource)
}
