package dev.opencab.core.data.connection

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Capped exponential backoff with decorrelated jitter.
 *
 * Attempt `n` waits `base * 2^(n-1)`, capped at `max`, then multiplies by a random factor in
 * `[1 - jitter, 1]`. The jitter spreads reconnect storms when several phones reconnect to the
 * same layout at once. A jitter fraction of `0` makes the policy deterministic for tests.
 */
class ReconnectPolicy(
    private val baseMillis: Long,
    private val maxMillis: Long,
    private val jitterFraction: Double,
    private val random: Random,
) {
    init {
        require(baseMillis > 0) { "Base delay must be positive" }
        require(maxMillis >= baseMillis) { "Max delay must be at least the base" }
        require(jitterFraction in 0.0..1.0) { "Jitter fraction must be between 0 and 1" }
    }

    /** Delay before reconnect attempt [attempt], which is one-based. */
    fun delayMillis(attempt: Int): Long {
        require(attempt >= 1) { "Attempt must be at least 1" }
        val exponent = (attempt - 1).coerceAtMost(MAX_SHIFT)
        val exponential = minOf(maxMillis, baseMillis shl exponent)
        if (jitterFraction == 0.0) return exponential

        val lowerBound = (exponential * (1.0 - jitterFraction)).roundToLong()
            .coerceIn(1, exponential)
        return if (lowerBound >= exponential) {
            exponential
        } else {
            random.nextLong(lowerBound, exponential + 1)
        }
    }

    companion object {
        private const val MAX_SHIFT = 20

        fun from(config: DccClientConfig): ReconnectPolicy = ReconnectPolicy(
            baseMillis = config.reconnectBaseMillis,
            maxMillis = config.reconnectMaxMillis,
            jitterFraction = config.reconnectJitterFraction,
            random = config.random,
        )
    }
}
