package dev.opencab.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.opencab.core.data.connection.ConnectionRepository
import dev.opencab.core.data.connection.SavedServerStore
import dev.opencab.core.data.connection.ServerDiscovery
import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ConnectionState
import dev.opencab.core.model.ConnectionTarget
import dev.opencab.core.model.DiscoveredServer
import dev.opencab.core.model.SavedServer
import dev.opencab.core.model.ServerEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the connection screen's immutable state.
 *
 * It coordinates discovery, saved layouts, the manual sheet, permission state, and the app-scoped
 * [ConnectionRepository]. Composables send intents and render [ConnectionUiState]; they never
 * touch a socket, a protocol type, or a system resource.
 */
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val discovery: ServerDiscovery,
    private val savedServerStore: SavedServerStore,
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = mutableUiState.asStateFlow()

    private val eventChannel = Channel<ConnectionEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var savedServers: List<SavedServer> = emptyList()
    private var discoveredServers: List<DiscoveredServer> = emptyList()
    private var permission = PermissionUiState.Checking
    private var scanPhase = ScanPhase.Idle
    private var discoveryError: ConnectionError? = null
    private var manualEntry: ManualEntryUiState? = null
    private var scanJob: Job? = null

    private var connectionState: ConnectionState = connectionRepository.state.value
    private var lastObservedState: ConnectionState = connectionRepository.state.value

    init {
        viewModelScope.launch {
            savedServerStore.servers.collect { servers ->
                savedServers = servers
                publish()
            }
        }
        viewModelScope.launch {
            connectionRepository.state.collect { state ->
                val wasConnected = lastObservedState is ConnectionState.Connected
                lastObservedState = state
                connectionState = state
                publish()
                if (state is ConnectionState.Connected && !wasConnected) {
                    rememberConnection(state.target)
                    eventChannel.send(ConnectionEvent.OpenDrive)
                }
            }
        }
    }

    /** Called when the screen enters the foreground and whenever it resumes. */
    fun onPermissionChecked(granted: Boolean) {
        if (granted) {
            val wasGranted = permission == PermissionUiState.Granted
            permission = PermissionUiState.Granted
            if (!wasGranted) {
                startScan(force = true)
            } else if (scanPhase == ScanPhase.Idle) {
                startScan(force = false)
            }
        } else {
            scanJob?.cancel()
            scanJob = null
            if (scanPhase == ScanPhase.Scanning) scanPhase = ScanPhase.Complete
            if (permission != PermissionUiState.Denied) permission = PermissionUiState.Needed
        }
        publish()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            permission = PermissionUiState.Granted
            startScan(force = true)
        } else {
            permission = PermissionUiState.Denied
            scanJob?.cancel()
            scanJob = null
            scanPhase = ScanPhase.Complete
            publish()
        }
    }

    fun onRescan() {
        if (permission == PermissionUiState.Granted) {
            startScan(force = true)
        }
    }

    fun onConnect(server: ServerCardUiState) {
        connect(ConnectionTarget(name = server.name, endpoint = server.endpoint))
    }

    fun onCancelConnection() {
        connectionRepository.cancel()
    }

    fun onRetry() {
        val target = (connectionState as? ConnectionState.Failed)?.target
            ?: mutableUiState.value.link?.target
            ?: return
        connect(target)
    }

    fun onDismissFailure() {
        connectionRepository.disconnect()
    }

    fun onDisconnect() {
        connectionRepository.disconnect()
    }

    fun onOpenManualEntry() {
        manualEntry = ManualEntryUiState()
        publish()
    }

    fun onEditSaved(server: ServerCardUiState) {
        manualEntry =
            ManualEntryUiState(
                editingId = server.id,
                name = server.name,
                host = server.endpoint.host,
                port = server.endpoint.port.toString(),
            )
        publish()
    }

    fun onDeleteSaved(server: ServerCardUiState) {
        viewModelScope.launch {
            savedServerStore.delete(server.id)
        }
    }

    fun onCancelManualEntry() {
        manualEntry = null
        publish()
    }

    fun onManualNameChanged(name: String) {
        updateManualEntry { it.copy(name = name) }
    }

    fun onManualHostChanged(host: String) {
        updateManualEntry { it.copy(host = host, hostError = null) }
    }

    fun onManualPortChanged(port: String) {
        updateManualEntry { it.copy(port = port, portError = null) }
    }

    fun onSubmitManualEntry() {
        val entry = manualEntry ?: return
        val validation = ManualServerValidator.validate(host = entry.host, port = entry.port)
        if (!validation.isValid) {
            manualEntry =
                entry.copy(
                    hostError = validation.hostError,
                    portError = validation.portError,
                )
            publish()
            return
        }

        val host = entry.host.trim()
        val port = entry.port.trim().toInt()
        val endpoint = ServerEndpoint(host = host, port = port)
        val name = entry.name.trim().ifBlank { host }
        val editingId = entry.editingId

        viewModelScope.launch {
            if (editingId != null) {
                val previous = savedServers.firstOrNull { it.id == editingId }
                savedServerStore.upsert(
                    SavedServer(
                        id = editingId,
                        name = name,
                        endpoint = endpoint,
                        lastConnectedEpochMillis = previous?.lastConnectedEpochMillis,
                    ),
                )
                manualEntry = null
                publish()
            } else {
                val existing = savedServers.firstOrNull { it.endpoint == endpoint }
                val server = existing?.copy(name = name)
                    ?: SavedServer.create(name = name, endpoint = endpoint)
                savedServerStore.upsert(server)
                manualEntry = null
                publish()
                connect(ConnectionTarget(name = name, endpoint = endpoint))
            }
        }
    }

    private fun connect(target: ConnectionTarget) {
        scanJob?.cancel()
        scanJob = null
        if (scanPhase == ScanPhase.Scanning) scanPhase = ScanPhase.Complete
        connectionRepository.connect(target)
        publish()
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun startScan(force: Boolean) {
        if (permission != PermissionUiState.Granted) return
        if (!force && (scanPhase == ScanPhase.Scanning || scanPhase == ScanPhase.Complete)) return

        scanJob?.cancel()
        discoveredServers = emptyList()
        discoveryError = null
        scanPhase = ScanPhase.Scanning
        publish()

        scanJob = viewModelScope.launch {
            try {
                discovery.scan(ServerDiscovery.SCAN_TIMEOUT_MILLIS).collect { snapshot ->
                    discoveredServers = snapshot.servers
                    discoveryError = snapshot.error
                    if (snapshot.error == ConnectionError.LocalNetworkPermissionDenied) {
                        permission = PermissionUiState.Denied
                    }
                    if (snapshot.finished) scanPhase = ScanPhase.Complete
                    publish()
                }
                if (scanPhase == ScanPhase.Scanning) {
                    scanPhase = ScanPhase.Complete
                    publish()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (security: SecurityException) {
                permission = PermissionUiState.Denied
                discoveryError = ConnectionError.LocalNetworkPermissionDenied
                scanPhase = ScanPhase.Complete
                publish()
            } catch (_: Exception) {
                discoveryError = ConnectionError.Unknown
                scanPhase = ScanPhase.Complete
                publish()
            }
        }
    }

    private fun updateManualEntry(transform: (ManualEntryUiState) -> ManualEntryUiState) {
        manualEntry = manualEntry?.let(transform)
        publish()
    }

    private suspend fun rememberConnection(target: ConnectionTarget) {
        runCatching {
            savedServerStore.recordConnected(
                name = target.name,
                endpoint = target.endpoint,
                nowEpochMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun publish() {
        val link = when (val state = connectionState) {
            is ConnectionState.Connecting ->
                LinkUiState(target = state.target, phase = LinkPhase.Connecting)

            is ConnectionState.Connected ->
                LinkUiState(target = state.target, phase = LinkPhase.Connected)

            is ConnectionState.Reconnecting ->
                LinkUiState(
                    target = state.target,
                    phase = LinkPhase.Reconnecting,
                    attempt = state.attempt,
                )

            ConnectionState.Disconnected, is ConnectionState.Failed -> null
        }
        val failure = (connectionState as? ConnectionState.Failed)?.let {
            FailureUiState(target = it.target, error = it.error)
        }

        mutableUiState.value =
            ConnectionUiState(
                permission = permission,
                scanPhase = scanPhase,
                discovered = discoveredServers.map { server ->
                    serverCard(
                        id = "discovered:${server.endpoint.display}",
                        name = server.name,
                        endpoint = server.endpoint,
                        lastConnectedEpochMillis = savedServers
                            .firstOrNull { it.endpoint == server.endpoint }
                            ?.lastConnectedEpochMillis,
                        source = ServerSource.Discovered,
                    )
                },
                saved = savedServers.map { server ->
                    serverCard(
                        id = server.id,
                        name = server.name,
                        endpoint = server.endpoint,
                        lastConnectedEpochMillis = server.lastConnectedEpochMillis,
                        source = ServerSource.Saved,
                    )
                },
                link = link,
                failure = failure,
                manualEntry = manualEntry,
                discoveryError = discoveryError,
            )
    }

    private fun serverCard(
        id: String,
        name: String,
        endpoint: ServerEndpoint,
        lastConnectedEpochMillis: Long?,
        source: ServerSource,
    ): ServerCardUiState = ServerCardUiState(
        id = id,
        name = name,
        endpoint = endpoint,
        lastConnectedEpochMillis = lastConnectedEpochMillis,
        source = source,
        status = statusFor(endpoint),
    )

    private fun statusFor(endpoint: ServerEndpoint): ServerStatus = when (val state = connectionState) {
        is ConnectionState.Connecting ->
            if (state.target.endpoint == endpoint) ServerStatus.Connecting else ServerStatus.Available

        is ConnectionState.Connected ->
            if (state.target.endpoint == endpoint) ServerStatus.Connected else ServerStatus.Available

        is ConnectionState.Reconnecting ->
            if (state.target.endpoint == endpoint) ServerStatus.Connecting else ServerStatus.Available

        is ConnectionState.Failed ->
            if (state.target.endpoint == endpoint) ServerStatus.Failed else ServerStatus.Available

        ConnectionState.Disconnected -> ServerStatus.Available
    }
}
