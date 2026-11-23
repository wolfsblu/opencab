package dev.opencab.feature.connection

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
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
            dispatchAction(
                action = action,
                viewModel = viewModel,
                permissionLauncher = permissionLauncher,
                context = context,
                onBack = onBack,
                onOpenDrive = onOpenDrive,
            )
        },
        onManualNameChange = viewModel::onManualNameChanged,
        onManualHostChange = viewModel::onManualHostChanged,
        onManualPortChange = viewModel::onManualPortChanged,
        modifier = modifier,
    )
}

private fun dispatchAction(
    action: ConnectionAction,
    viewModel: ConnectionViewModel,
    permissionLauncher: ActivityResultLauncher<String>,
    context: Context,
    onBack: () -> Unit,
    onOpenDrive: () -> Unit,
) {
    when (action) {
        is ConnectionAction.Navigation -> handleNavigation(action, onBack, onOpenDrive)

        is ConnectionAction.Permission ->
            handlePermission(action, viewModel, permissionLauncher, context)

        is ConnectionAction.Server -> handleServer(action, viewModel)

        is ConnectionAction.Link -> handleLink(action, viewModel)

        is ConnectionAction.Manual -> handleManual(action, viewModel)
    }
}

private fun handleNavigation(
    action: ConnectionAction.Navigation,
    onBack: () -> Unit,
    onOpenDrive: () -> Unit,
) {
    when (action) {
        ConnectionAction.Back -> onBack()
        ConnectionAction.OpenDrive -> onOpenDrive()
    }
}

private fun handlePermission(
    action: ConnectionAction.Permission,
    viewModel: ConnectionViewModel,
    permissionLauncher: ActivityResultLauncher<String>,
    context: Context,
) {
    when (action) {
        ConnectionAction.RequestPermission -> {
            val permission = LocalNetworkPermission.requiredPermission(context)
            if (permission == null) {
                viewModel.onPermissionResult(granted = true)
            } else {
                permissionLauncher.launch(permission)
            }
        }

        ConnectionAction.OpenSettings -> LocalNetworkPermission.openSettings(context)
    }
}

private fun handleServer(
    action: ConnectionAction.Server,
    viewModel: ConnectionViewModel,
) {
    when (action) {
        ConnectionAction.Rescan -> viewModel.onRescan()
        is ConnectionAction.Connect -> viewModel.onConnect(action.server)
        is ConnectionAction.EditSaved -> viewModel.onEditSaved(action.server)
        is ConnectionAction.DeleteSaved -> viewModel.onDeleteSaved(action.server)
    }
}

private fun handleLink(
    action: ConnectionAction.Link,
    viewModel: ConnectionViewModel,
) {
    when (action) {
        ConnectionAction.CancelConnection -> viewModel.onCancelConnection()
        ConnectionAction.Retry -> viewModel.onRetry()
        ConnectionAction.DismissFailure -> viewModel.onDismissFailure()
        ConnectionAction.Disconnect -> viewModel.onDisconnect()
    }
}

private fun handleManual(
    action: ConnectionAction.Manual,
    viewModel: ConnectionViewModel,
) {
    when (action) {
        ConnectionAction.OpenManualEntry -> viewModel.onOpenManualEntry()
        ConnectionAction.CancelManualEntry -> viewModel.onCancelManualEntry()
        ConnectionAction.SubmitManualEntry -> viewModel.onSubmitManualEntry()
    }
}
