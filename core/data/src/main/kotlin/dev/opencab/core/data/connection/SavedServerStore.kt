package dev.opencab.core.data.connection

import dev.opencab.core.model.SavedServer
import dev.opencab.core.model.ServerEndpoint
import kotlinx.coroutines.flow.Flow

/** Persistent layouts the operator has connected to before. */
interface SavedServerStore {
    val servers: Flow<List<SavedServer>>

    suspend fun upsert(server: SavedServer)

    suspend fun delete(id: String)

    /**
     * Records a successful connection, creating the saved entry when this endpoint is new.
     * Matching is by host:port so a renamed layout is not duplicated.
     */
    suspend fun recordConnected(
        name: String,
        endpoint: ServerEndpoint,
        nowEpochMillis: Long,
    ): SavedServer
}
