package dev.opencab.feature.connection

import androidx.compose.runtime.Composable
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ConnectionMode
import dev.opencab.core.model.ConnectionTarget
import dev.opencab.core.model.ServerEndpoint

private val junctionYard = ServerEndpoint(host = "192.168.1.24", port = 12090)
private val testBench = ServerEndpoint(host = "192.168.1.31", port = 12090)

private fun savedServer(
    name: String,
    endpoint: ServerEndpoint,
    lastConnectedHoursAgo: Long?,
): ServerCardUiState = ServerCardUiState(
    id = "saved:$name",
    name = name,
    endpoint = endpoint,
    lastConnectedEpochMillis = lastConnectedHoursAgo?.let {
        System.currentTimeMillis() - it * 60L * 60L * 1_000L
    },
    source = ServerSource.Saved,
)

private fun discoveredServer(
    name: String,
    endpoint: ServerEndpoint,
): ServerCardUiState = ServerCardUiState(
    id = "discovered:${endpoint.display}",
    name = name,
    endpoint = endpoint,
    source = ServerSource.Discovered,
)

@CabPreview
@Composable
private fun ScanningPreview() {
    Preview(
        ConnectionUiState(
            permission = PermissionUiState.Granted,
            scanPhase = ScanPhase.Scanning,
        ),
    )
}

@CabPreview
@Composable
private fun EmptyPreview() {
    Preview(
        ConnectionUiState(
            permission = PermissionUiState.Granted,
            scanPhase = ScanPhase.Complete,
            saved = listOf(savedServer("Home", junctionYard, lastConnectedHoursAgo = 26)),
        ),
    )
}

@CabPreview
@Composable
private fun PermissionDeniedPreview() {
    Preview(
        ConnectionUiState(
            permission = PermissionUiState.Denied,
            scanPhase = ScanPhase.Complete,
            saved = listOf(savedServer("Home", junctionYard, lastConnectedHoursAgo = 26)),
        ),
    )
}

@CabPreview
@Composable
private fun ConnectingPreview() {
    val target = ConnectionTarget(name = "Junction Yard", endpoint = junctionYard)
    Preview(
        ConnectionUiState(
            permission = PermissionUiState.Granted,
            scanPhase = ScanPhase.Complete,
            discovered = listOf(discoveredServer("Junction Yard", junctionYard)),
            link = LinkUiState(target = target, phase = LinkPhase.Connecting),
        ),
    )
}

@CabPreview
@Composable
private fun FailedPreview() {
    val target = ConnectionTarget(name = "Junction Yard", endpoint = junctionYard)
    Preview(
        ConnectionUiState(
            permission = PermissionUiState.Granted,
            scanPhase = ScanPhase.Complete,
            discovered = listOf(
                discoveredServer("Junction Yard", junctionYard).copy(status = ServerStatus.Failed),
            ),
            failure = FailureUiState(target = target, error = ConnectionError.TimedOut),
            saved = listOf(savedServer("Home", testBench, lastConnectedHoursAgo = 240)),
        ),
    )
}

@CabPreview
@Composable
private fun ConnectedPreview() {
    val target = ConnectionTarget(name = "Junction Yard", endpoint = junctionYard)
    Preview(
        ConnectionUiState(
            permission = PermissionUiState.Granted,
            scanPhase = ScanPhase.Complete,
            saved = listOf(
                savedServer("Junction Yard", junctionYard, lastConnectedHoursAgo = 0)
                    .copy(status = ServerStatus.Connected),
                savedServer("Home", testBench, lastConnectedHoursAgo = 240),
            ),
            link = LinkUiState(target = target, phase = LinkPhase.Connected),
        ),
    )
}

@CabPreview
@Composable
private fun DemoConnectedPreview() {
    val target = ConnectionTarget(
        name = ConnectionTarget.DEMO_NAME,
        endpoint = ServerEndpoint("demo", ServerEndpoint.DEFAULT_PORT),
        mode = ConnectionMode.DEMO,
    )
    Preview(
        ConnectionUiState(
            permission = PermissionUiState.Denied,
            scanPhase = ScanPhase.Complete,
            link = LinkUiState(target = target, phase = LinkPhase.Connected),
        ),
    )
}

@Composable
private fun Preview(state: ConnectionUiState) {
    CabTheme {
        ConnectionScreen(
            uiState = state,
            onAction = {},
            onManualNameChange = {},
            onManualHostChange = {},
            onManualPortChange = {},
        )
    }
}
