package dev.opencab.core.data.roster

import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterLoco
import dev.opencab.core.model.RosterLocoDraft
import dev.opencab.core.model.ServerRosterEntry
import kotlinx.coroutines.flow.Flow

/** Why a roster write was rejected. UI layers map these to their own copy. */
enum class RosterValidationError {
    NAME_REQUIRED,
    NOT_FOUND,
}

/** The outcome of a create or update. */
sealed interface RosterSaveResult {
    /**
     * [createdNewEntry] is true for a create and for an edit that changed the address. Changing
     * an address preserves the old row and appends a new one so history is never rewritten.
     */
    data class Saved(
        val loco: RosterLoco,
        val createdNewEntry: Boolean,
    ) : RosterSaveResult

    data class DuplicateAddress(val existing: RosterLoco) : RosterSaveResult

    data class Invalid(val reason: RosterValidationError) : RosterSaveResult
}

/** Persistence boundary for the local locomotive roster. */
interface RosterRepository {
    val locos: Flow<List<RosterLoco>>

    fun observeLoco(id: String): Flow<RosterLoco?>

    suspend fun loco(id: String): RosterLoco?

    suspend fun findByAddress(address: RosterAddress): RosterLoco?

    suspend fun create(draft: RosterLocoDraft): RosterSaveResult

    suspend fun update(
        id: String,
        draft: RosterLocoDraft,
    ): RosterSaveResult

    suspend fun delete(id: String)

    suspend fun markUsed(id: String)

    /** Stores the layout's labels for the current acquisition without touching local labels. */
    suspend fun recordServerFunctionLabels(
        address: RosterAddress,
        labels: Map<Int, String>,
    )

    /** Adds layout roster entries that are not already present. Returns the number added. */
    suspend fun importServerRoster(entries: List<ServerRosterEntry>): Int
}
