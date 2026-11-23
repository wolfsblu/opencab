package dev.opencab.core.data.throttle

import dev.opencab.core.data.connection.DccClient
import dev.opencab.core.data.connection.DccConnectionState
import dev.opencab.core.model.ServerEndpoint
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

internal data class CommandAttempt(
    val command: WiThrottleCommand,
    val atMillis: Long,
    val accepted: Boolean,
)

internal class FakeDccClient(
    private val clock: () -> Long,
) : DccClient {
    private val mutableState = MutableStateFlow<DccConnectionState>(DccConnectionState.Disconnected)
    override val state: StateFlow<DccConnectionState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<WiThrottleEvent>(
        replay = 32,
        extraBufferCapacity = 32,
    )
    override val events: Flow<WiThrottleEvent> = mutableEvents.asSharedFlow()

    val attempts = mutableListOf<CommandAttempt>()
    var sendPolicy: (WiThrottleCommand) -> Boolean = { true }

    override suspend fun connect(endpoint: ServerEndpoint) = Unit

    override suspend fun send(command: WiThrottleCommand): Boolean = record(command)

    override fun trySend(command: WiThrottleCommand): Boolean = record(command)

    override fun close() {
        mutableState.value = DccConnectionState.Disconnected
    }

    fun emitState(state: DccConnectionState) {
        mutableState.value = state
    }

    fun emitEvent(event: WiThrottleEvent) {
        mutableEvents.tryEmit(event)
    }

    fun commands(): List<WiThrottleCommand> = attempts.filter { it.accepted }.map { it.command }

    private fun record(command: WiThrottleCommand): Boolean {
        val accepted = sendPolicy(command)
        attempts += CommandAttempt(command, clock(), accepted)
        return accepted
    }
}

internal fun connected(
    endpoint: ServerEndpoint = ServerEndpoint("127.0.0.1"),
): DccConnectionState.Connected = DccConnectionState.Connected(
    endpoint = endpoint,
    serverVersion = ProtocolVersion.V2_0,
    heartbeatIntervalMillis = 5_000,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.controller(
    client: FakeDccClient,
    config: ThrottleConfig = ThrottleConfig(),
): ThrottleController {
    val controller = ThrottleController(
        scope = backgroundScope,
        config = config,
        nowMillis = { testScheduler.currentTime },
    )
    controller.link(client)
    runCurrent()
    return controller
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.connect(client: FakeDccClient) {
    client.emitState(connected())
    runCurrent()
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.acquire(
    controller: ThrottleController,
    client: FakeDccClient,
    address: LocoAddress,
    speedSteps: SpeedSteps = SpeedSteps.ONE_HUNDRED_TWENTY_EIGHT,
) {
    controller.acquire(address, speedSteps = speedSteps)
    runCurrent()
    client.emitEvent(
        WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, LocoId.of(address)),
    )
    runCurrent()
}
