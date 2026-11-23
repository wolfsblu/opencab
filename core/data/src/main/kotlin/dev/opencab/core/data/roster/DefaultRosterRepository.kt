package dev.opencab.core.data.roster

import dev.opencab.core.data.db.FunctionLabelCodec
import dev.opencab.core.data.db.RosterLocoDao
import dev.opencab.core.data.db.RosterLocoEntity
import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.model.RosterColor
import dev.opencab.core.model.RosterIcon
import dev.opencab.core.model.RosterLoco
import dev.opencab.core.model.RosterLocoDraft
import dev.opencab.core.model.ServerRosterEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Room-backed roster.
 *
 * The unique address index is the last line of defence. This class checks first so the caller
 * receives a typed [RosterSaveResult.DuplicateAddress] instead of an SQLite exception.
 */
class DefaultRosterRepository(
    private val dao: RosterLocoDao,
    private val clock: EpochClock,
) : RosterRepository {
    override val locos: Flow<List<RosterLoco>> =
        dao.observeAll().map { entities -> entities.mapNotNull(RosterLocoEntity::toRosterLoco) }

    override fun observeLoco(id: String): Flow<RosterLoco?> = dao.observeById(id).map { it?.toRosterLoco() }

    override suspend fun loco(id: String): RosterLoco? = dao.findById(id)?.toRosterLoco()

    override suspend fun findByAddress(
        address: RosterAddress,
    ): RosterLoco? = dao.findByAddress(address.number, address.type.name)?.toRosterLoco()

    override suspend fun create(draft: RosterLocoDraft): RosterSaveResult {
        val name = draft.name.trim()
        if (name.isEmpty()) return RosterSaveResult.Invalid(RosterValidationError.NAME_REQUIRED)
        val duplicate = dao.findByAddress(draft.address.number, draft.address.type.name)
        if (duplicate != null) {
            val existing = duplicate.toRosterLoco()
            return if (existing == null) {
                RosterSaveResult.Invalid(RosterValidationError.NOT_FOUND)
            } else {
                RosterSaveResult.DuplicateAddress(existing)
            }
        }
        val entity = draft.toEntity(
            id = UUID.randomUUID().toString(),
            createdAtEpochMillis = clock.nowMillis(),
            lastUsedEpochMillis = null,
            serverFunctionLabels = emptyMap(),
        )
        dao.insert(entity)
        val saved = entity.toRosterLoco()
        return if (saved == null) {
            RosterSaveResult.Invalid(RosterValidationError.NOT_FOUND)
        } else {
            RosterSaveResult.Saved(loco = saved, createdNewEntry = true)
        }
    }

    override suspend fun update(
        id: String,
        draft: RosterLocoDraft,
    ): RosterSaveResult {
        val name = draft.name.trim()
        if (name.isEmpty()) return RosterSaveResult.Invalid(RosterValidationError.NAME_REQUIRED)
        val existing = dao.findById(id) ?: return RosterSaveResult.Invalid(RosterValidationError.NOT_FOUND)
        val addressChanged =
            existing.address != draft.address.number || existing.addressType != draft.address.type.name
        return if (addressChanged) {
            replaceWithNewAddress(existing, draft, name)
        } else {
            updateInPlace(existing, draft, name)
        }
    }

    private suspend fun updateInPlace(
        existing: RosterLocoEntity,
        draft: RosterLocoDraft,
        name: String,
    ): RosterSaveResult {
        val updated = existing.copy(
            name = name,
            functionLabels = FunctionLabelCodec.encode(draft.localFunctionLabels),
            isFavorite = draft.isFavorite,
            colorKey = draft.color.key,
            iconKey = draft.icon.key,
            serverRosterId = draft.serverRosterId ?: existing.serverRosterId,
        )
        dao.update(updated)
        val saved = updated.toRosterLoco()
            ?: return RosterSaveResult.Invalid(RosterValidationError.NOT_FOUND)
        return RosterSaveResult.Saved(loco = saved, createdNewEntry = false)
    }

    private suspend fun replaceWithNewAddress(
        existing: RosterLocoEntity,
        draft: RosterLocoDraft,
        name: String,
    ): RosterSaveResult {
        val duplicate = dao.findByAddress(draft.address.number, draft.address.type.name)
        if (duplicate != null) {
            val clash = duplicate.toRosterLoco()
                ?: return RosterSaveResult.Invalid(RosterValidationError.NOT_FOUND)
            return RosterSaveResult.DuplicateAddress(clash)
        }
        val replacement = existing.copy(
            id = UUID.randomUUID().toString(),
            name = name,
            address = draft.address.number,
            addressType = draft.address.type.name,
            functionLabels = FunctionLabelCodec.encode(draft.localFunctionLabels),
            isFavorite = draft.isFavorite,
            colorKey = draft.color.key,
            iconKey = draft.icon.key,
            // A corrected address must be acquired directly; the layout's roster id still
            // points at the old address.
            serverRosterId = null,
            createdAtEpochMillis = clock.nowMillis(),
        )
        dao.insert(replacement)
        val saved = replacement.toRosterLoco()
            ?: return RosterSaveResult.Invalid(RosterValidationError.NOT_FOUND)
        return RosterSaveResult.Saved(loco = saved, createdNewEntry = true)
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun markUsed(id: String) {
        dao.markUsed(id, clock.nowMillis())
    }

    override suspend fun recordServerFunctionLabels(
        address: RosterAddress,
        labels: Map<Int, String>,
    ) {
        val existing = dao.findByAddress(address.number, address.type.name) ?: return
        val encoded = FunctionLabelCodec.encode(labels)
        if (existing.serverFunctionLabels == encoded) return
        dao.updateServerFunctionLabels(existing.id, encoded)
    }

    override suspend fun importServerRoster(entries: List<ServerRosterEntry>): Int {
        var imported = 0
        for (entry in entries) {
            if (dao.findByAddress(entry.address.number, entry.address.type.name) != null) continue
            val name = entry.name.trim().ifEmpty { "Loco ${entry.address.number}" }
            val entity = RosterLocoEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                address = entry.address.number,
                addressType = entry.address.type.name,
                functionLabels = "",
                serverFunctionLabels = "",
                isFavorite = false,
                colorKey = RosterColor.BLUE.key,
                iconKey = RosterIcon.STEAM.key,
                lastUsedEpochMillis = null,
                serverRosterId = entry.id,
                createdAtEpochMillis = clock.nowMillis(),
            )
            dao.insert(entity)
            imported += 1
        }
        return imported
    }
}

private fun RosterLocoEntity.toRosterLoco(): RosterLoco? {
    val type = runCatching { RosterAddressType.valueOf(addressType) }.getOrNull() ?: return null
    val address = runCatching { RosterAddress(number = address, type = type) }.getOrNull() ?: return null
    return RosterLoco(
        id = id,
        name = name,
        address = address,
        localFunctionLabels = FunctionLabelCodec.decode(functionLabels),
        serverFunctionLabels = FunctionLabelCodec.decode(serverFunctionLabels),
        isFavorite = isFavorite,
        color = RosterColor.fromKey(colorKey),
        icon = RosterIcon.fromKey(iconKey),
        lastUsedEpochMillis = lastUsedEpochMillis,
        serverRosterId = serverRosterId,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

private fun RosterLocoDraft.toEntity(
    id: String,
    createdAtEpochMillis: Long,
    lastUsedEpochMillis: Long?,
    serverFunctionLabels: Map<Int, String>,
): RosterLocoEntity = RosterLocoEntity(
    id = id,
    name = name.trim(),
    address = address.number,
    addressType = address.type.name,
    functionLabels = FunctionLabelCodec.encode(localFunctionLabels),
    serverFunctionLabels = FunctionLabelCodec.encode(serverFunctionLabels),
    isFavorite = isFavorite,
    colorKey = color.key,
    iconKey = icon.key,
    lastUsedEpochMillis = lastUsedEpochMillis,
    serverRosterId = serverRosterId,
    createdAtEpochMillis = createdAtEpochMillis,
)
