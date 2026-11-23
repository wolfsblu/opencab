package dev.opencab.feature.connection

import dev.opencab.core.data.connection.ConnectionRepository
import dev.opencab.core.data.connection.DiscoverySnapshot
import dev.opencab.core.data.connection.SavedServerStore
import dev.opencab.core.data.connection.ServerDiscovery
import dev.opencab.core.model.ConnectionState
import dev.opencab.core.model.ConnectionTarget
import dev.opencab.core.model.SavedServer
import dev.opencab.core.model.ServerEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

internal class FakeServerDiscovery(
    private val snapshot: DiscoverySnapshot = DiscoverySnapshot(finished = true),
    private val failWith: Throwable? = null,
) : ServerDiscovery {
    var scanCount: Int = 0
        private set
    var lastTimeoutMillis: Long? = null
        private set

    override fun scan(timeoutMillis: Long): Flow<DiscoverySnapshot> {
        scanCount += 1
        lastTimeoutMillis = timeoutMillis
        return if (failWith != null) {
            flow { throw failWith }
        } else {
            flow { emit(snapshot) }
        }
    }
}

internal class FakeSavedServerStore(
    initial: List<SavedServer> = emptyList(),
) : SavedServerStore {
    private val state = MutableStateFlow(initial)
    override val servers: Flow<List<SavedServer>> = state.asStateFlow()

    val upserts = mutableListOf<SavedServer>()
    val deletions = mutableListOf<String>()

    override suspend fun upsert(server: SavedServer) {
        upserts += server
        state.value = state.value.filterNot { it.id == server.id || it.endpoint == server.endpoint } + server
    }

    override suspend fun delete(id: String) {
        deletions += id
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun recordConnected(
        name: String,
        endpoint: ServerEndpoint,
        nowEpochMillis: Long,
    ): SavedServer {
        val existing = state.value.firstOrNull { it.endpoint == endpoint }
        val server = existing?.copy(lastConnectedEpochMillis = nowEpochMillis)
            ?: SavedServer.create(name = name, endpoint = endpoint, lastConnectedEpochMillis = nowEpochMillis)
        upsert(server)
        return server
    }

    fun emit(servers: List<SavedServer>) {
        state.value = servers
    }
}

internal class FakeConnectionRepository : ConnectionRepository {
    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = mutableState.asStateFlow()

    val connectedTargets = mutableListOf<ConnectionTarget>()
    var cancelCount: Int = 0
        private set
    var disconnectCount: Int = 0
        private set

    override fun connect(target: ConnectionTarget) {
        connectedTargets += target
        mutableState.value = ConnectionState.Connecting(target)
    }

    override fun cancel() {
        cancelCount += 1
        mutableState.value = ConnectionState.Disconnected
    }

    override fun disconnect() {
        disconnectCount += 1
        mutableState.value = ConnectionState.Disconnected
    }

    fun emit(connectionState: ConnectionState) {
        mutableState.value = connectionState
    }
}
