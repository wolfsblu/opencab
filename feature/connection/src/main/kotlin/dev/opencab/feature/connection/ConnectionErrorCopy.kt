package dev.opencab.feature.connection

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.opencab.core.model.ConnectionError

/** What the operator can do about a failure. Never a raw exception or protocol message. */
internal enum class ConnectionErrorAction {
    Retry,
    OpenSettings,
    Dismiss,
}

internal data class ConnectionErrorCopy(
    val title: String,
    val message: String,
    val action: ConnectionErrorAction,
)

@Composable
internal fun connectionErrorCopy(
    error: ConnectionError,
    hostPort: String,
): ConnectionErrorCopy = when (error) {
    ConnectionError.LocalNetworkPermissionDenied ->
        ConnectionErrorCopy(
            title = stringResource(R.string.connection_error_permission_title),
            message = stringResource(R.string.connection_error_permission_body),
            action = ConnectionErrorAction.OpenSettings,
        )

    ConnectionError.TimedOut ->
        ConnectionErrorCopy(
            title = stringResource(R.string.connection_error_timeout_title),
            message = stringResource(R.string.connection_error_timeout_body, hostPort),
            action = ConnectionErrorAction.Retry,
        )

    ConnectionError.HostNotFound ->
        ConnectionErrorCopy(
            title = stringResource(R.string.connection_error_host_title),
            message = stringResource(R.string.connection_error_host_body, hostPort),
            action = ConnectionErrorAction.Retry,
        )

    ConnectionError.ConnectionRefused ->
        ConnectionErrorCopy(
            title = stringResource(R.string.connection_error_refused_title),
            message = stringResource(R.string.connection_error_refused_body, hostPort),
            action = ConnectionErrorAction.Retry,
        )

    ConnectionError.Unreachable ->
        ConnectionErrorCopy(
            title = stringResource(R.string.connection_error_unreachable_title),
            message = stringResource(R.string.connection_error_unreachable_body),
            action = ConnectionErrorAction.Retry,
        )

    ConnectionError.ServerClosed ->
        ConnectionErrorCopy(
            title = stringResource(R.string.connection_error_closed_title),
            message = stringResource(R.string.connection_error_closed_body),
            action = ConnectionErrorAction.Retry,
        )

    is ConnectionError.UnsupportedVersion ->
        ConnectionErrorCopy(
            title = stringResource(R.string.connection_error_version_title),
            message = stringResource(
                R.string.connection_error_version_body,
                error.required,
                error.found,
            ),
            action = ConnectionErrorAction.Dismiss,
        )

    ConnectionError.NotWiThrottle ->
        ConnectionErrorCopy(
            title = stringResource(R.string.connection_error_not_withrottle_title),
            message = stringResource(R.string.connection_error_not_withrottle_body),
            action = ConnectionErrorAction.Retry,
        )

    ConnectionError.ProtocolMismatch ->
        ConnectionErrorCopy(
            title = stringResource(R.string.connection_error_protocol_title),
            message = stringResource(R.string.connection_error_protocol_body),
            action = ConnectionErrorAction.Dismiss,
        )

    ConnectionError.Unknown ->
        ConnectionErrorCopy(
            title = stringResource(R.string.connection_error_unknown_title),
            message = stringResource(R.string.connection_error_unknown_body),
            action = ConnectionErrorAction.Retry,
        )
}
