package dev.opencab.core.data.roster

/** Injectable wall clock so persistence timestamps are deterministic under test. */
fun interface EpochClock {
    fun nowMillis(): Long
}
