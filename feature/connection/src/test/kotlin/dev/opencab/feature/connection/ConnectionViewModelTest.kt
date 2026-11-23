package dev.opencab.feature.connection

import dev.opencab.core.data.connection.DiscoverySnapshot
import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ConnectionMode
import dev.opencab.core.model.ConnectionState
import dev.opencab.core.model.ConnectionTarget
import dev.opencab.core.model.DiscoveredServer
import dev.opencab.core.model.SavedServer
import dev.opencab.core.model.ServerEndpoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val endpoint = ServerEndpoint(host = "192.168.1.24", port = 12090)
    private val discovered = DiscoveredServer(name = "Junction Yard", endpoint = endpoint)

    @Test
    fun grantedPermissionStartsScanAndPublishesResults() = runTest {
        val discovery = FakeServerDiscovery(
            DiscoverySnapshot(servers = listOf(discovered), finished = true),
        )
        val viewModel = viewModel(discovery = discovery)

        viewModel.onPermissionChecked(granted = true)
        advanceUntilIdle()

        assertEquals(PermissionUiState.Granted, viewModel.uiState.value.permission)
        assertEquals(ScanPhase.Complete, viewModel.uiState.value.scanPhase)
        assertEquals(1, discovery.scanCount)
        assertEquals(1, viewModel.uiState.value.discovered.size)
        assertEquals("Junction Yard", viewModel.uiState.value.discovered.single().name)
        assertEquals(endpoint.display, viewModel.uiState.value.discovered.single().hostPort)
    }

    @Test
    fun deniedPermissionDoesNotScanAndShowsDeniedAfterTheSystemResult() = runTest {
        val discovery = FakeServerDiscovery()
        val viewModel = viewModel(discovery = discovery)

        viewModel.onPermissionChecked(granted = false)
        advanceUntilIdle()
        assertEquals(PermissionUiState.Needed, viewModel.uiState.value.permission)
        assertEquals(0, discovery.scanCount)

        viewModel.onPermissionResult(granted = false)
        advanceUntilIdle()
        assertEquals(PermissionUiState.Denied, viewModel.uiState.value.permission)
        assertEquals(0, discovery.scanCount)
    }

    @Test
    fun rescanIsForcedAfterACompletedScan() = runTest {
        val discovery = FakeServerDiscovery(DiscoverySnapshot(finished = true))
        val viewModel = viewModel(discovery = discovery)
        viewModel.onPermissionChecked(granted = true)
        advanceUntilIdle()
        assertEquals(1, discovery.scanCount)

        viewModel.onRescan()
        advanceUntilIdle()

        assertEquals(2, discovery.scanCount)
    }

    @Test
    fun connectingAndConnectedStatesAreProjectedAndOpenDriveIsEmitted() = runTest {
        val repository = FakeConnectionRepository()
        val viewModel = viewModel(repository = repository)
        val events = mutableListOf<ConnectionEvent>()
        val collector = launch { viewModel.events.collect { events += it } }
        val target = ConnectionTarget(name = "Junction Yard", endpoint = endpoint)

        viewModel.onConnect(
            ServerCardUiState(
                id = "discovered:${endpoint.display}",
                name = "Junction Yard",
                endpoint = endpoint,
                source = ServerSource.Discovered,
            ),
        )
        advanceUntilIdle()
        assertEquals(listOf(target), repository.connectedTargets)
        assertEquals(LinkPhase.Connecting, viewModel.uiState.value.link?.phase)

        repository.emit(ConnectionState.Connected(target))
        advanceUntilIdle()

        assertEquals(LinkPhase.Connected, viewModel.uiState.value.link?.phase)
        assertEquals(listOf(ConnectionEvent.OpenDrive), events)
        collector.cancel()
    }

    @Test
    fun failureIsExposedAsTypedDomainErrorAndRetryReconnects() = runTest {
        val repository = FakeConnectionRepository()
        val viewModel = viewModel(repository = repository)
        val target = ConnectionTarget(name = "Junction Yard", endpoint = endpoint)
        repository.emit(ConnectionState.Failed(target, ConnectionError.TimedOut))
        advanceUntilIdle()

        assertEquals(ConnectionError.TimedOut, viewModel.uiState.value.failure?.error)
        assertEquals(endpoint.display, viewModel.uiState.value.failure?.target?.endpoint?.display)

        viewModel.onRetry()
        advanceUntilIdle()
        assertEquals(listOf(target), repository.connectedTargets)
    }

    @Test
    fun manualEntryRejectsAnInvalidHostAndPort() = runTest {
        val repository = FakeConnectionRepository()
        val viewModel = viewModel(repository = repository)
        viewModel.onOpenManualEntry()
        viewModel.onManualHostChanged("http://192.168.1.24")
        viewModel.onManualPortChanged("0")

        viewModel.onSubmitManualEntry()
        advanceUntilIdle()

        assertEquals(ManualFieldError.InvalidHost, viewModel.uiState.value.manualEntry?.hostError)
        assertEquals(ManualFieldError.InvalidPort, viewModel.uiState.value.manualEntry?.portError)
        assertTrue(repository.connectedTargets.isEmpty())
    }

    @Test
    fun manualEntrySavesWithTheDefaultPortAndConnects() = runTest {
        val repository = FakeConnectionRepository()
        val store = FakeSavedServerStore()
        val viewModel = viewModel(repository = repository, store = store)
        viewModel.onOpenManualEntry()
        viewModel.onManualNameChanged("Test bench")
        viewModel.onManualHostChanged("192.168.1.31")

        viewModel.onSubmitManualEntry()
        advanceUntilIdle()

        val saved = store.upserts.single()
        assertEquals("Test bench", saved.name)
        assertEquals("192.168.1.31", saved.endpoint.host)
        assertEquals(ServerEndpoint.DEFAULT_PORT, saved.endpoint.port)
        assertEquals(ConnectionTarget("Test bench", saved.endpoint), repository.connectedTargets.single())
        assertNull(viewModel.uiState.value.manualEntry)
    }

    @Test
    fun demoStartsADemoTargetAndNeverSavesIt() = runTest {
        val repository = FakeConnectionRepository()
        val store = FakeSavedServerStore()
        val viewModel = viewModel(repository = repository, store = store)

        viewModel.onStartDemo()
        advanceUntilIdle()

        assertEquals(ConnectionMode.DEMO, repository.connectedTargets.single().mode)
        assertTrue(store.upserts.isEmpty())
    }

    @Test
    fun savedServersAreProjectedWithLastConnectedAndStatus() = runTest {
        val saved = SavedServer(
            id = "saved-1",
            name = "Home",
            endpoint = endpoint,
            lastConnectedEpochMillis = 1_700_000_000_000,
        )
        val store = FakeSavedServerStore(listOf(saved))
        val viewModel = viewModel(store = store)
        advanceUntilIdle()

        val card = viewModel.uiState.value.saved.single()
        assertEquals("Home", card.name)
        assertEquals(endpoint.display, card.hostPort)
        assertEquals(1_700_000_000_000, card.lastConnectedEpochMillis)
        assertEquals(ServerStatus.Available, card.status)
    }

    @Test
    fun successfulConnectionRecordsTheSavedLayout() = runTest {
        val repository = FakeConnectionRepository()
        val store = FakeSavedServerStore()
        val viewModel = viewModel(repository = repository, store = store)
        advanceUntilIdle()

        val target = ConnectionTarget("Junction Yard", endpoint)
        repository.emit(ConnectionState.Connected(target))
        advanceUntilIdle()

        assertEquals("Junction Yard", store.upserts.single().name)
        assertEquals(endpoint, store.upserts.single().endpoint)
    }

    @Test
    fun discoveredRowsReuseTheLastConnectedTimeFromItsSavedEndpoint() = runTest {
        val lastConnected = 1_700_000_000_000
        val discovery = FakeServerDiscovery(
            DiscoverySnapshot(servers = listOf(discovered), finished = true),
        )
        val store = FakeSavedServerStore(
            listOf(
                SavedServer(
                    id = "saved-1",
                    name = "Home",
                    endpoint = endpoint,
                    lastConnectedEpochMillis = lastConnected,
                ),
            ),
        )
        val viewModel = viewModel(discovery = discovery, store = store)
        viewModel.onPermissionChecked(granted = true)
        advanceUntilIdle()

        val card = viewModel.uiState.value.discovered.single()
        assertEquals(lastConnected, card.lastConnectedEpochMillis)
        assertEquals(ServerSource.Discovered, card.source)
    }

    private fun viewModel(
        discovery: FakeServerDiscovery = FakeServerDiscovery(),
        store: FakeSavedServerStore = FakeSavedServerStore(),
        repository: FakeConnectionRepository = FakeConnectionRepository(),
    ): ConnectionViewModel = ConnectionViewModel(
        discovery = discovery,
        savedServerStore = store,
        connectionRepository = repository,
    )
}
