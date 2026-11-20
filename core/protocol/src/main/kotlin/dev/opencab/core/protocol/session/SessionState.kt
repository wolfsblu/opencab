package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.ConsistInfo
import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.RosterLoco
import dev.opencab.core.protocol.SpeedStepMode
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.TrackPowerState

/** Whether the transport currently has a socket. */
enum class LinkState {
    DISCONNECTED,
    CONNECTED,
}

/** Handshake phase owned by [SessionReducer]. */
sealed interface HandshakeState {
    /** No socket yet. */
    data object Idle : HandshakeState

    /** Link is up and the client greeting was sent; the server's `VN` is still outstanding. */
    data object AwaitingGreeting : HandshakeState

    /** A supported `VN` was received; control commands may be sent. */
    data object Ready : HandshakeState

    /** The server's protocol version is below the configured minimum. */
    data class UnsupportedVersion(val version: ProtocolVersion) : HandshakeState
}

/** Server metadata gathered from the initial greeting. */
data class ServerInfo(
    val protocolVersion: ProtocolVersion? = null,
    val type: String? = null,
    val description: String? = null,
    val webPort: Int? = null,
)

/** Heartbeat policy state. All scheduling uses caller-supplied timestamps. */
data class HeartbeatState(
    val required: Boolean = false,
    val intervalSeconds: Int? = null,
    val monitoringEnabled: Boolean = false,
    val lastSentAtMillis: Long? = null,
)

/** Local acquisition state for one locomotive. */
enum class LocoStatus {
    IDLE,
    REQUESTED,
    ACQUIRED,
    STEAL_PENDING,
    STEALING,
    RELEASING,
    LOST,
}

/** Provenance of the speed value, mirroring ADR-0001's "never lie about state" rule. */
enum class SpeedSource {
    UNKNOWN,
    COMMANDED,
    LIVE,
    LAST_KNOWN,
}

/** Function state and momentary/latching classification. */
data class FunctionSlot(
    val isOn: Boolean = false,
    val isMomentary: Boolean = false,
)

/** Everything the session knows about one locomotive. */
data class LocoSlot(
    val address: LocoAddress,
    val name: String? = null,
    val rosterEntryId: String? = null,
    val status: LocoStatus = LocoStatus.IDLE,
    val desired: Boolean = false,
    val throttleKey: ThrottleKey? = null,
    val speed: Int = 0,
    val direction: Direction = Direction.FORWARD,
    val speedStepMode: SpeedStepMode = SpeedStepMode.NmraDcc128,
    val emergencyStop: Boolean = false,
    val speedSource: SpeedSource = SpeedSource.UNKNOWN,
    val functions: Map<Int, FunctionSlot> = emptyMap(),
    val functionLabels: List<String> = emptyList(),
) {
    fun function(number: Int): FunctionSlot = functions[number] ?: FunctionSlot()
}

/** Immutable snapshot produced by [SessionReducer]. */
data class SessionState(
    val link: LinkState = LinkState.DISCONNECTED,
    val handshake: HandshakeState = HandshakeState.Idle,
    val server: ServerInfo = ServerInfo(),
    val power: TrackPowerState = TrackPowerState.UNKNOWN,
    val heartbeat: HeartbeatState = HeartbeatState(),
    val locos: Map<LocoAddress, LocoSlot> = emptyMap(),
    val roster: List<RosterLoco> = emptyList(),
    val consists: List<ConsistInfo> = emptyList(),
    val consistCount: Int? = null,
    val lastAlert: String? = null,
    val lastInfo: String? = null,
    val lastOutboundAtMillis: Long? = null,
    val unknownMessageCount: Int = 0,
) {
    val canControl: Boolean
        get() = link == LinkState.CONNECTED && handshake == HandshakeState.Ready

    fun loco(address: LocoAddress): LocoSlot? = locos[address]
}
