package dev.opencab.core.protocol

/** A roster entry included in the server's initial `RL` greeting. */
data class RosterLoco(
    val name: String,
    val address: LocoAddress,
)

/** One locomotive in an advanced consist (`RCD`). */
data class ConsistMember(
    val address: LocoAddress,
    val forward: Boolean,
)

/** The server's view of one advanced consist (`RCD`). */
data class ConsistInfo(
    val address: LocoAddress,
    val id: String,
    val members: List<ConsistMember>,
)

/**
 * A decoded server-to-client WiThrottle message.
 *
 * All unknown, malformed, or unsupported lines are represented by [Unknown] rather than thrown.
 * The decoder logs [Unknown] through [ProtocolLogger].
 */
sealed interface WiThrottleEvent {
    /** `VN2.0` */
    data class Version(val version: ProtocolVersion) : WiThrottleEvent

    /** `*10` - heartbeat/E-stop interval in seconds. `0` means heartbeat is not required. */
    data class HeartbeatInterval(val seconds: Int) : WiThrottleEvent

    /** `PPA0`, `PPA1`, or `PPA2`. */
    data class TrackPower(val state: TrackPowerState) : WiThrottleEvent

    /** `HTJMRI` */
    data class ServerType(val type: String) : WiThrottleEvent

    /** `HtJMRI 5.x My Railroad` */
    data class ServerDescription(val description: String) : WiThrottleEvent

    /** `HM` plus message text - normally an error to show the user. */
    data class Alert(val message: String) : WiThrottleEvent

    /** `Hm` plus message text - informational. */
    data class Info(val message: String) : WiThrottleEvent

    /** `PW12080` */
    data class WebPort(val port: Int) : WiThrottleEvent

    /** `RL2]\[name}|{41}|{L...` */
    data class RosterList(
        val declaredCount: Int,
        val entries: List<RosterLoco>,
    ) : WiThrottleEvent

    /** `RCC2` (editable) or `RCL2` (selection-only); both declare the consist count. */
    data class ConsistListHeader(
        val declaredCount: Int,
        val editable: Boolean,
    ) : WiThrottleEvent

    /** `RCD}|{74(S)}|{74(S)]\[3374(L)}|{true...` */
    data class ConsistEntry(val consist: ConsistInfo) : WiThrottleEvent

    /** `PFT65871<;>4` - decoded for completeness; the v1 session ignores it. */
    data class FastClock(
        val epochSeconds: Long,
        val ratio: Double?,
    ) : WiThrottleEvent

    /** `M0+L341<;>` - the server granted a locomotive acquisition. */
    data class LocoAdded(
        val throttleKey: ThrottleKey,
        val locoId: LocoId,
    ) : WiThrottleEvent

    /** `M0-L341<;>` - the server removed a locomotive. */
    data class LocoRemoved(
        val throttleKey: ThrottleKey,
        val locoId: LocoId,
    ) : WiThrottleEvent

    /** `M0SL341<;>L341` - a steal confirmation is required. */
    data class StealRequested(
        val throttleKey: ThrottleKey,
        val locoId: LocoId,
    ) : WiThrottleEvent

    /** `M0LL41<;>]\[Headlight]\[Bell]\[` - labels ordered by function number. */
    data class FunctionLabels(
        val throttleKey: ThrottleKey,
        val locoId: LocoId,
        val labels: List<String>,
    ) : WiThrottleEvent

    /** `M0AL341<;>V23` or `M0AL341<;>V-126` */
    data class SpeedChanged(
        val throttleKey: ThrottleKey,
        val locoId: LocoId,
        val speed: Int,
        val emergencyStop: Boolean,
    ) : WiThrottleEvent

    /** `M0AL341<;>R1` */
    data class DirectionChanged(
        val throttleKey: ThrottleKey,
        val locoId: LocoId,
        val direction: Direction,
    ) : WiThrottleEvent

    /** `M0AL341<;>s1` */
    data class SpeedStepModeChanged(
        val throttleKey: ThrottleKey,
        val locoId: LocoId,
        val mode: SpeedStepMode,
    ) : WiThrottleEvent

    /** `M0AL341<;>F10` - function 1 is on. */
    data class FunctionStateChanged(
        val throttleKey: ThrottleKey,
        val locoId: LocoId,
        val function: Int,
        val isOn: Boolean,
    ) : WiThrottleEvent

    /** `M0AL341<;>m10` - function 1 is momentary. */
    data class FunctionMomentaryChanged(
        val throttleKey: ThrottleKey,
        val locoId: LocoId,
        val function: Int,
        val isMomentary: Boolean,
    ) : WiThrottleEvent

    /**
     * An unrecognised or unsupported line. The decoder never fails on this; it logs the reason and
     * returns the raw line so diagnostics can display it.
     */
    data class Unknown(
        val raw: String,
        val reason: String,
    ) : WiThrottleEvent
}
