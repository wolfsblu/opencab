package dev.opencab.feature.roster

import dev.opencab.core.data.roster.RosterRepository
import dev.opencab.core.data.roster.RosterSaveResult
import dev.opencab.core.data.roster.RosterValidationError
import dev.opencab.core.data.throttle.PrimaryAcquireResult
import dev.opencab.core.data.throttle.ThrottleUiSource
import dev.opencab.core.data.throttle.ThrottleUiState
import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterLoco
import dev.opencab.core.model.RosterLocoDraft
import dev.opencab.core.model.ServerRosterEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal class FakeRosterRepository(
    initial: List<RosterLoco> = emptyList(),
) : RosterRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = 1
    var now: Long = 10_000L

    val created = mutableListOf<RosterLocoDraft>()
    val updated = mutableListOf<Pair<String, RosterLocoDraft>>()
    val markedUsed = mutableListOf<String>()
    val imported = mutableListOf<List<ServerRosterEntry>>()
    var createResult: RosterSaveResult? = null
    var updateResult: RosterSaveResult? = null

    override val locos: Flow<List<RosterLoco>> = state

    val current: List<RosterLoco>
        get() = state.value

    override fun observeLoco(id: String): Flow<RosterLoco?> = state.map { entries ->
        entries.firstOrNull { it.id == id }
    }

    override suspend fun loco(id: String): RosterLoco? = state.value.firstOrNull { it.id == id }

    override suspend fun findByAddress(
        address: RosterAddress,
    ): RosterLoco? = state.value.firstOrNull { it.address == address }

    override suspend fun create(draft: RosterLocoDraft): RosterSaveResult {
        created += draft
        createResult?.let { return it }
        val duplicate = state.value.firstOrNull { it.address == draft.address }
        if (duplicate != null) return RosterSaveResult.DuplicateAddress(duplicate)
        val loco = draft.toLoco("created-${nextId++}")
        state.update { it + loco }
        return RosterSaveResult.Saved(loco, createdNewEntry = true)
    }

    override suspend fun update(
        id: String,
        draft: RosterLocoDraft,
    ): RosterSaveResult {
        updated += id to draft
        updateResult?.let { return it }
        val existing = state.value.firstOrNull { it.id == id }
            ?: return RosterSaveResult.Invalid(RosterValidationError.NOT_FOUND)
        val duplicate = state.value.firstOrNull { it.id != id && it.address == draft.address }
        if (duplicate != null) return RosterSaveResult.DuplicateAddress(duplicate)
        val replacement = if (existing.address == draft.address) {
            existing.copy(
                name = draft.name,
                localFunctionLabels = draft.localFunctionLabels,
                isFavorite = draft.isFavorite,
                color = draft.color,
                icon = draft.icon,
            )
        } else {
            draft.toLoco("created-${nextId++}")
        }
        state.update { entries ->
            if (existing.address == draft.address) {
                entries.map { if (it.id == id) replacement else it }
            } else {
                entries + replacement
            }
        }
        return RosterSaveResult.Saved(replacement, createdNewEntry = existing.address != draft.address)
    }

    override suspend fun delete(id: String) {
        state.update { entries -> entries.filterNot { it.id == id } }
    }

    override suspend fun markUsed(id: String) {
        markedUsed += id
        state.update { entries ->
            entries.map { if (it.id == id) it.copy(lastUsedEpochMillis = now) else it }
        }
    }

    override suspend fun recordServerFunctionLabels(
        address: RosterAddress,
        labels: Map<Int, String>,
    ) {
        state.update { entries ->
            entries.map {
                if (it.address == address) it.copy(serverFunctionLabels = labels) else it
            }
        }
    }

    override suspend fun importServerRoster(entries: List<ServerRosterEntry>): Int {
        imported += entries
        val existing = state.value.map { it.address }.toSet()
        val added = entries.filterNot { it.address in existing }
        state.update { current ->
            current + added.map { it.toLoco("imported-${nextId++}") }
        }
        return added.size
    }
}

internal class FakeLocoAcquirer : LocoAcquirer {
    val acquired = mutableListOf<RosterLoco>()
    var result: PrimaryAcquireResult = PrimaryAcquireResult.REQUESTED

    override fun acquire(loco: RosterLoco): PrimaryAcquireResult {
        acquired += loco
        return result
    }
}

internal class FakeThrottleUiSource(
    initial: ThrottleUiState = ThrottleUiState(),
) : ThrottleUiSource {
    val state = MutableStateFlow(initial)
    override val uiState = state
}

private fun RosterLocoDraft.toLoco(id: String): RosterLoco = RosterLoco(
    id = id,
    name = name,
    address = address,
    localFunctionLabels = localFunctionLabels,
    isFavorite = isFavorite,
    color = color,
    icon = icon,
    createdAtEpochMillis = 1L,
)

private fun ServerRosterEntry.toLoco(id: String): RosterLoco = RosterLoco(
    id = id,
    name = name,
    address = address,
    serverRosterId = this.id,
)
