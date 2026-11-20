package dev.opencab.core.model

/**
 * A locomotive known to the roster.
 *
 * Persistence and editing land in a later milestone.
 */
data class RosterEntry(
    val id: String,
    val name: String,
    val roadNumber: String,
)
