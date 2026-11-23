package dev.opencab.feature.connection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

const val CONNECTION_ROUTE: String = "connection"

/**
 * Route wrapper for the connection screen.
 *
 * Platform concerns live here: the permission launcher, the Settings deep link, and collecting
 * one-shot navigation events. Everything below this function is pure Compose over UI state.
 */
@Composable
fun ConnectionRoute(
    onBack: () -> Unit,
    onOpenDrive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(viewModel) {
        viewModel.onPermissionChecked(LocalNetworkPermission.isGranted(context))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onPermissionChecked(LocalNetworkPermission.isGranted(context))
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ConnectionEvent.OpenDrive -> onOpenDrive()
            }
        }
    }

    ConnectionScreen(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                ConnectionAction.Back -> onBack()

                ConnectionAction.Rescan -> viewModel.onRescan()

                is ConnectionAction.Connect -> viewModel.onConnect(action.server)

                is ConnectionAction.EditSaved -> viewModel.onEditSaved(action.server)

                is ConnectionAction.DeleteSaved -> viewModel.onDeleteSaved(action.server)

                ConnectionAction.CancelConnection -> viewModel.onCancelConnection()

                ConnectionAction.Retry -> viewModel.onRetry()

                ConnectionAction.DismissFailure -> viewModel.onDismissFailure()

                ConnectionAction.RequestPermission -> {
                    val permission = LocalNetworkPermission.requiredPermission(context)
                    if (permission == null) {
                        viewModel.onPermissionResult(granted = true)
                    } else {
                        permissionLauncher.launch(permission)
                    }
                }

                ConnectionAction.OpenSettings ->
                    LocalNetworkPermission.openSettings(context)

                ConnectionAction.OpenDrive -> onOpenDrive()

                ConnectionAction.OpenManualEntry -> viewModel.onOpenManualEntry()

                ConnectionAction.CancelManualEntry -> viewModel.onCancelManualEntry()

                ConnectionAction.SubmitManualEntry -> viewModel.onSubmitManualEntry()

                ConnectionAction.StartDemo -> viewModel.onStartDemo()

                ConnectionAction.EndDemo -> viewModel.onEndDemo()
            }
        },
        onManualNameChange = viewModel::onManualNameChanged,
        onManualHostChange = viewModel::onManualHostChanged,
        onManualPortChange = viewModel::onManualPortChanged,
        modifier = modifier,
    )
}
