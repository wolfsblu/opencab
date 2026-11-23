package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ServerEndpoint
import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.LocoSelection
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.RosterLoco
import dev.opencab.core.protocol.SpeedStepMode
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.TrackPowerState
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A scripted, in-process DCC server for hardware-free use.
 *
 * It implements the same [DccClient] contract as [WiThrottleClient]: the handshake exposes a
 * `VN2.0` greeting and a small roster, acquisitions are acknowledged with the same `M` events a
 * real server sends, and speed/function commands are echoed back. A simulated drop moves the link
 * to `Reconnecting` and then brings it back automatically, so the throttle resilience path can be
 * exercised without a layout.
 */
@Singleton
class DemoTransport @Inject constructor() : DccClient {
    private val lock = Any()
    private val mutableState = MutableStateFlow<DccConnectionState>(DccConnectionState.Disconnected)
    override val state: StateFlow<DccConnectionState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<WiThrottleEvent>(
        replay = EVENT_REPLAY,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<WiThrottleEvent> = mutableEvents.asSharedFlow()

    @Volatile
    private var session: Session? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun connect(endpoint: ServerEndpoint) {
        awaitPreviousSession()
        mutableEvents.resetReplayCache()
        val newSession = Session()
        synchronized(lock) {
            check(session == null) { "DemoTransport is already connected" }
            session = newSession
        }
        try {
            coroutineScope {
                newSession.job = currentCoroutineContext()[Job]
                if (newSession.closing) throw CancellationException()
                run(endpoint, newSession)
            }
        } finally {
            newSession.closing = true
            newSession.commands.close()
            newSession.drops.close()
            synchronized(lock) {
                if (session === newSession) session = null
            }
            if (mutableState.value !is DccConnectionState.Failed) {
                mutableState.value = DccConnectionState.Disconnected
            }
            newSession.finished.complete(Unit)
        }
    }

    override suspend fun send(command: WiThrottleCommand): Boolean {
        val current = session ?: return false
        if (!current.connected || current.closing) return false
        return try {
            current.commands.send(command)
            true
        } catch (_: ClosedSendChannelException) {
            false
        }
    }

    override fun trySend(command: WiThrottleCommand): Boolean {
        val current = session ?: return false
        if (!current.connected || current.closing) return false
        return current.commands.trySend(command).isSuccess
    }

    override fun close() {
        val current = synchronized(lock) { session }
        if (current == null) {
            mutableState.value = DccConnectionState.Disconnected
            return
        }
        current.closing = true
        current.commands.close()
        current.drops.close()
        current.job?.cancel()
        mutableState.value = DccConnectionState.Disconnected
    }

    /**
     * Simulates the layout dropping the link. The session enters `Reconnecting` and returns to
     * `Connected` after [RECONNECT_DELAY_MILLIS], re-sending the greeting.
     */
    fun simulateDisconnect(): Boolean {
        val current = session ?: return false
        if (!current.connected) return false
        return current.drops.trySend(Unit).isSuccess
    }

    private suspend fun awaitPreviousSession() {
        val previous = synchronized(lock) { session } ?: return
        close()
        previous.finished.await()
    }

    private suspend fun run(
        endpoint: ServerEndpoint,
        session: Session,
    ) {
        mutableState.value = DccConnectionState.Connecting(endpoint)
        delay(CONNECT_DELAY_MILLIS)

        var attempt = 0
        while (currentCoroutineContext().isActive && !session.closing) {
            if (attempt > 0) {
                mutableState.value = DccConnectionState.Reconnecting(
                    endpoint = endpoint,
                    attempt = attempt,
                    lastError = ConnectionError.ServerClosed,
                    retryInMillis = RECONNECT_DELAY_MILLIS,
                )
                delay(RECONNECT_DELAY_MILLIS)
                if (session.closing) return
            }

            session.connected = true
            mutableState.value = DccConnectionState.Connected(
                endpoint = endpoint,
                serverVersion = ProtocolVersion.V2_0,
                heartbeatIntervalMillis = 0L,
            )
            emitGreeting()
            pump(session)
            session.connected = false
            if (session.closing) return
            attempt += 1
        }
    }

    private suspend fun pump(session: Session) {
        while (currentCoroutineContext().isActive && !session.closing) {
            var dropped = false
            var stop = false
            select<Unit> {
                session.commands.onReceiveCatching { result ->
                    if (result.isClosed) {
                        stop = true
                        return@onReceiveCatching
                    }
                    handle(result.getOrThrow(), session)
                }
                session.drops.onReceiveCatching { result ->
                    if (result.isSuccess) dropped = true else stop = true
                }
            }
            if (stop || dropped) return
        }
    }

    private suspend fun emitGreeting() {
        mutableEvents.emit(WiThrottleEvent.Version(ProtocolVersion.V2_0))
        mutableEvents.emit(WiThrottleEvent.ServerType("JMRI"))
        mutableEvents.emit(WiThrottleEvent.ServerDescription("OpenCab demo layout"))
        mutableEvents.emit(WiThrottleEvent.TrackPower(TrackPowerState.ON))
        mutableEvents.emit(WiThrottleEvent.HeartbeatInterval(0))
        mutableEvents.emit(
            WiThrottleEvent.RosterList(
                declaredCount = DEMO_LOCOS.size,
                entries = DEMO_LOCOS.map { RosterLoco(it.name, it.address) },
            ),
        )
    }

    private suspend fun handle(
        command: WiThrottleCommand,
        session: Session,
    ) {
        when (command) {
            is WiThrottleCommand.Acquire -> handleAcquire(command, session)

            is WiThrottleCommand.Release -> handleRelease(command, session)

            is WiThrottleCommand.SetSpeed,
            is WiThrottleCommand.SetDirection,
            is WiThrottleCommand.SetIdle,
            is WiThrottleCommand.EmergencyStop,
            -> handleMotion(command, session)

            is WiThrottleCommand.PressFunction,
            is WiThrottleCommand.ReleaseFunction,
            is WiThrottleCommand.ForceFunction,
            is WiThrottleCommand.SetFunctionMomentary,
            is WiThrottleCommand.QueryMomentaryStates,
            -> handleFunction(command, session)

            is WiThrottleCommand.SetSpeedStepMode,
            is WiThrottleCommand.QuerySpeed,
            is WiThrottleCommand.QueryDirection,
            is WiThrottleCommand.QuerySpeedStepMode,
            -> handleQuery(command, session)

            is WiThrottleCommand.Steal ->
                mutableEvents.emit(WiThrottleEvent.LocoAdded(command.throttleKey, LocoId.of(command.locoKey)))

            is WiThrottleCommand.SetTrackPower ->
                mutableEvents.emit(WiThrottleEvent.TrackPower(command.state))

            is WiThrottleCommand.SetHardwareId,
            is WiThrottleCommand.SetDeviceName,
            WiThrottleCommand.Heartbeat,
            WiThrottleCommand.EnableHeartbeatMonitoring,
            WiThrottleCommand.DisableHeartbeatMonitoring,
            WiThrottleCommand.Quit,
            is WiThrottleCommand.ConsistAdd,
            is WiThrottleCommand.ConsistRemoveLoco,
            is WiThrottleCommand.ConsistSetOrder,
            is WiThrottleCommand.ConsistDelete,
            is WiThrottleCommand.SetConsistLead,
            -> Unit
        }
    }

    private suspend fun handleAcquire(
        command: WiThrottleCommand.Acquire,
        session: Session,
    ) {
        val address = addressOf(command.selection) ?: return
        val previous = session.acquiredAddress
        if (previous != null && previous != address) {
            session.acquiredAddress = null
            mutableEvents.emit(
                WiThrottleEvent.LocoRemoved(
                    throttleKey = command.throttleKey,
                    locoId = LocoId.of(previous),
                ),
            )
        }
        session.acquiredAddress = address
        session.throttleKey = command.throttleKey
        val locoId = LocoId.of(address)
        mutableEvents.emit(WiThrottleEvent.LocoAdded(command.throttleKey, locoId))
        mutableEvents.emit(
            WiThrottleEvent.FunctionLabels(
                throttleKey = command.throttleKey,
                locoId = locoId,
                labels = DEMO_FUNCTION_LABELS,
            ),
        )
        mutableEvents.emit(
            WiThrottleEvent.SpeedChanged(
                throttleKey = command.throttleKey,
                locoId = locoId,
                speed = session.speed,
                emergencyStop = false,
            ),
        )
        mutableEvents.emit(
            WiThrottleEvent.DirectionChanged(
                throttleKey = command.throttleKey,
                locoId = locoId,
                direction = session.direction,
            ),
        )
        mutableEvents.emit(
            WiThrottleEvent.SpeedStepModeChanged(
                throttleKey = command.throttleKey,
                locoId = locoId,
                mode = SpeedStepMode.NmraDcc128,
            ),
        )
    }

    private suspend fun handleRelease(
        command: WiThrottleCommand.Release,
        session: Session,
    ) {
        val locoId = command.target
        if (locoId is LocoId.All || locoId == session.acquiredAddress?.let(LocoId::of)) {
            session.acquiredAddress = null
        }
        mutableEvents.emit(WiThrottleEvent.LocoRemoved(command.throttleKey, locoId))
    }

    private suspend fun handleMotion(
        command: WiThrottleCommand,
        session: Session,
    ) {
        when (command) {
            is WiThrottleCommand.SetSpeed -> emitSpeed(session, command, command.speed)

            is WiThrottleCommand.SetDirection -> {
                session.direction = command.direction
                emitDirection(command)
            }

            is WiThrottleCommand.SetIdle -> emitSpeed(session, command, 0)

            is WiThrottleCommand.EmergencyStop ->
                emitSpeed(session, command, 0, emergencyStop = true)

            else -> Unit
        }
    }

    private suspend fun handleFunction(
        command: WiThrottleCommand,
        session: Session,
    ) {
        when (command) {
            is WiThrottleCommand.PressFunction -> {
                val next = session.functions[command.function] != true
                session.functions[command.function] = next
                emitFunction(command, command.function, next)
            }

            is WiThrottleCommand.ReleaseFunction -> {
                session.functions[command.function] = false
                emitFunction(command, command.function, false)
            }

            is WiThrottleCommand.ForceFunction -> {
                session.functions[command.function] = command.on
                emitFunction(command, command.function, command.on)
            }

            is WiThrottleCommand.SetFunctionMomentary ->
                mutableEvents.emit(
                    WiThrottleEvent.FunctionMomentaryChanged(
                        throttleKey = command.throttleKey,
                        locoId = command.target,
                        function = command.function,
                        isMomentary = command.momentary,
                    ),
                )

            is WiThrottleCommand.QueryMomentaryStates ->
                DEMO_FUNCTION_LABELS.indices.forEach { function ->
                    mutableEvents.emit(
                        WiThrottleEvent.FunctionMomentaryChanged(
                            throttleKey = command.throttleKey,
                            locoId = command.target,
                            function = function,
                            isMomentary = function == MOMENTARY_FUNCTION,
                        ),
                    )
                }

            else -> Unit
        }
    }

    private suspend fun handleQuery(
        command: WiThrottleCommand,
        session: Session,
    ) {
        when (command) {
            is WiThrottleCommand.SetSpeedStepMode ->
                mutableEvents.emit(
                    WiThrottleEvent.SpeedStepModeChanged(
                        throttleKey = command.throttleKey,
                        locoId = command.target,
                        mode = command.mode,
                    ),
                )

            is WiThrottleCommand.QuerySpeed ->
                mutableEvents.emit(
                    WiThrottleEvent.SpeedChanged(
                        throttleKey = command.throttleKey,
                        locoId = command.target,
                        speed = session.speed,
                        emergencyStop = false,
                    ),
                )

            is WiThrottleCommand.QueryDirection ->
                mutableEvents.emit(
                    WiThrottleEvent.DirectionChanged(
                        throttleKey = command.throttleKey,
                        locoId = command.target,
                        direction = session.direction,
                    ),
                )

            is WiThrottleCommand.QuerySpeedStepMode ->
                mutableEvents.emit(
                    WiThrottleEvent.SpeedStepModeChanged(
                        throttleKey = command.throttleKey,
                        locoId = command.target,
                        mode = SpeedStepMode.NmraDcc128,
                    ),
                )

            else -> Unit
        }
    }

    private suspend fun emitSpeed(
        session: Session,
        command: WiThrottleCommand,
        speed: Int,
        emergencyStop: Boolean = false,
    ) {
        session.speed = if (emergencyStop) 0 else speed
        val throttleKey = when (command) {
            is WiThrottleCommand.SetSpeed -> command.throttleKey
            is WiThrottleCommand.SetIdle -> command.throttleKey
            is WiThrottleCommand.EmergencyStop -> command.throttleKey
            else -> session.throttleKey
        }
        val target = when (command) {
            is WiThrottleCommand.SetSpeed -> command.target
            is WiThrottleCommand.SetIdle -> command.target
            is WiThrottleCommand.EmergencyStop -> command.target
            else -> session.acquiredAddress?.let(LocoId::of) ?: LocoId.All
        }
        mutableEvents.emit(
            WiThrottleEvent.SpeedChanged(
                throttleKey = throttleKey,
                locoId = target,
                speed = speed,
                emergencyStop = emergencyStop,
            ),
        )
    }

    private suspend fun emitDirection(command: WiThrottleCommand.SetDirection) {
        mutableEvents.emit(
            WiThrottleEvent.DirectionChanged(
                throttleKey = command.throttleKey,
                locoId = command.target,
                direction = command.direction,
            ),
        )
    }

    private suspend fun emitFunction(
        command: WiThrottleCommand,
        function: Int,
        on: Boolean,
    ) {
        val throttleKey = when (command) {
            is WiThrottleCommand.PressFunction -> command.throttleKey
            is WiThrottleCommand.ReleaseFunction -> command.throttleKey
            is WiThrottleCommand.ForceFunction -> command.throttleKey
            else -> ThrottleKey.DEFAULT
        }
        val target = when (command) {
            is WiThrottleCommand.PressFunction -> command.target
            is WiThrottleCommand.ReleaseFunction -> command.target
            is WiThrottleCommand.ForceFunction -> command.target
            else -> LocoId.All
        }
        mutableEvents.emit(WiThrottleEvent.FunctionStateChanged(throttleKey, target, function, on))
    }

    private fun addressOf(selection: LocoSelection): LocoAddress? = when (selection) {
        is LocoSelection.Address -> selection.address

        is LocoSelection.RosterEntry ->
            DEMO_LOCOS.firstOrNull { it.rosterId == selection.id || it.name == selection.id }?.address
    }

    private class Session {
        @Volatile
        var job: Job? = null

        @Volatile
        var closing: Boolean = false

        @Volatile
        var connected: Boolean = false

        @Volatile
        var throttleKey: ThrottleKey = ThrottleKey.DEFAULT

        @Volatile
        var acquiredAddress: LocoAddress? = null

        @Volatile
        var speed: Int = 0

        @Volatile
        var direction: Direction = Direction.FORWARD

        val functions = mutableMapOf<Int, Boolean>()

        val commands = Channel<WiThrottleCommand>(COMMAND_BUFFER)

        val drops = Channel<Unit>(Channel.CONFLATED)

        val finished = CompletableDeferred<Unit>()
    }

    private data class DemoLoco(
        val rosterId: String,
        val name: String,
        val address: LocoAddress,
    )

    companion object {
        /** Long enough to see the connecting state, short enough to feel instant on a demo phone. */
        const val CONNECT_DELAY_MILLIS: Long = 450L

        /** Time spent visibly reconnecting after [simulateDisconnect]. */
        const val RECONNECT_DELAY_MILLIS: Long = 1_000L

        private const val MOMENTARY_FUNCTION = 2
        private const val COMMAND_BUFFER = 64
        private const val EVENT_REPLAY = 16
        private const val EVENT_BUFFER = 128

        private val DEMO_LOCOS = listOf(
            DemoLoco("demo-1", "Yard Goat", LocoAddress.short(3)),
            DemoLoco("demo-2", "Mainline Freight", LocoAddress.long(3412)),
        )

        private val DEMO_FUNCTION_LABELS = listOf(
            "Headlight",
            "Bell",
            "Horn",
            "Whistle",
            "Steam",
        )
    }
}
