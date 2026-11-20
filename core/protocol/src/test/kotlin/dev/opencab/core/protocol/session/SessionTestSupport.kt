package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent

internal fun ready(now: Long = 0): SessionReducer {
    val reducer = SessionReducer()
    reducer.awaitReady(now)
    return reducer
}

internal fun SessionReducer.awaitReady(now: Long = 0) {
    handle(SessionInput.LinkUp(now))
    send(WiThrottleEvent.Version(ProtocolVersion.V2_0), now + 1)
}

internal fun SessionReducer.send(
    event: WiThrottleEvent,
    at: Long,
): List<WiThrottleCommand> = handle(SessionInput.Event(event, at))

internal fun SessionReducer.send(
    intent: SessionIntent,
    at: Long,
): List<WiThrottleCommand> = handle(SessionInput.Intent(intent, at))

internal fun SessionReducer.acquired(
    address: LocoAddress,
    now: Long = 10,
) {
    send(SessionIntent.Acquire(address), now)
    send(WiThrottleEvent.LocoAdded(ThrottleKey.DEFAULT, LocoId.of(address)), now + 1)
}
