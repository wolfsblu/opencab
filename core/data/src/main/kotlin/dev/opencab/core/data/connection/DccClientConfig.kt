package dev.opencab.core.data.connection

import dev.opencab.core.protocol.ProtocolVersion
import kotlin.random.Random

/**
 * Timeouts, heartbeat policy, and reconnect policy for [WiThrottleClient].
 *
 * Defaults are the production values. Tests override them with short real-time windows; the
 * reconnect policy is separately unit-tested with a seeded [Random].
 */
data class DccClientConfig(
    val connectTimeoutMillis: Int = 4_000,
    val readTimeoutMillis: Int = 1_000,
    val greetingTimeoutMillis: Long = 5_000,
    val heartbeatIntervalMillis: Long = 5_000,
    /** Declare the link silent after this many negotiated heartbeat intervals. */
    val heartbeatSilenceMultiplier: Double = 2.0,
    /** How long the server has to answer the forced `N` liveness probe. */
    val heartbeatProbeTimeoutMillis: Long = 3_000,
    val reconnectBaseMillis: Long = 500,
    val reconnectMaxMillis: Long = 8_000,
    /** Fraction of the exponential delay that may be shaved off; 0.5 means 50–100 %. */
    val reconnectJitterFraction: Double = 0.5,
    val minimumProtocolVersion: ProtocolVersion = ProtocolVersion.V2_0,
    val random: Random = Random.Default,
) {
    init {
        require(connectTimeoutMillis > 0) { "Connect timeout must be positive" }
        require(readTimeoutMillis > 0) { "Read timeout must be positive" }
        require(greetingTimeoutMillis > 0) { "Greeting timeout must be positive" }
        require(heartbeatIntervalMillis > 0) { "Heartbeat interval must be positive" }
        require(heartbeatSilenceMultiplier >= 1.0) { "Heartbeat silence multiplier must be at least 1" }
        require(heartbeatProbeTimeoutMillis > 0) { "Heartbeat probe timeout must be positive" }
        require(reconnectBaseMillis > 0) { "Reconnect base must be positive" }
        require(reconnectMaxMillis >= reconnectBaseMillis) { "Reconnect max must be at least the base" }
        require(reconnectJitterFraction in 0.0..1.0) { "Jitter fraction must be between 0 and 1" }
    }
}
