package dev.opencab.feature.throttle

import dev.opencab.core.data.throttle.ThrottleAcquisitionState
import dev.opencab.core.data.throttle.ThrottleHealth
import dev.opencab.core.data.throttle.ThrottleIssue
import dev.opencab.core.data.throttle.ThrottleLinkState
import dev.opencab.core.data.throttle.ThrottleSlotUiState
import dev.opencab.core.data.throttle.ThrottleUiState
import dev.opencab.core.model.ConnectionState

/**
 * Maps the engine and connection snapshots into the screen model.
 *
 * The fallback to the connection state matters for the first frames after navigation: the
 * transport can already be connected before the engine's actor has processed its first state.
 */
internal fun mapDriveUiState(
    throttle: ThrottleUiState,
    connection: ConnectionState,
    scale: DriveScale? = null,
): DriveUiState {
    val link = mapLink(throttle.link, connection)
    val attempt = throttle.reconnectAttempt ?: (connection as? ConnectionState.Reconnecting)?.attempt
    return DriveUiState(
        layoutName = connection.targetName(),
        link = link,
        reconnectAttempt = attempt,
        loco = throttle.slots.firstOrNull()?.toDriveLoco(),
        scale = scale,
        trackPowerOn = throttle.trackPowerOn,
    )
}

private fun mapLink(
    link: ThrottleLinkState,
    connection: ConnectionState,
): DriveLink = when (link) {
    ThrottleLinkState.CONNECTED -> DriveLink.Connected

    ThrottleLinkState.CONNECTING -> DriveLink.Connecting

    ThrottleLinkState.RECONNECTING -> DriveLink.Reconnecting

    ThrottleLinkState.FAILED -> DriveLink.Failed

    ThrottleLinkState.DISCONNECTED,
    ThrottleLinkState.UNLINKED,
    -> when (connection) {
        is ConnectionState.Connected -> DriveLink.Connected
        is ConnectionState.Connecting -> DriveLink.Connecting
        is ConnectionState.Reconnecting -> DriveLink.Reconnecting
        is ConnectionState.Failed -> DriveLink.Failed
        ConnectionState.Disconnected -> DriveLink.Offline
    }
}

private fun ConnectionState.targetName(): String? = when (this) {
    is ConnectionState.Connected -> target.name
    is ConnectionState.Connecting -> target.name
    is ConnectionState.Reconnecting -> target.name
    is ConnectionState.Failed -> target.name
    ConnectionState.Disconnected -> null
}

private fun ThrottleSlotUiState.toDriveLoco(): DriveLoco = DriveLoco(
    address = addressDisplay,
    name = displayName,
    speedStepCount = speedSteps.steps,
    speedStep = step,
    speed = speed.toFloat(),
    direction = if (isForward) DriveDirection.Forward else DriveDirection.Reverse,
    functions = functions.map { function ->
        DriveFunction(
            number = function.number,
            label = function.label,
            isOn = function.isOn,
            isMomentary = function.isMomentary,
            isEnabled = function.isEnabled,
        )
    },
    health = when (health) {
        ThrottleHealth.ONLINE -> DriveHealth.Online
        ThrottleHealth.STALE -> DriveHealth.Stale
        ThrottleHealth.OFFLINE -> DriveHealth.Offline
    },
    acquisition = when (acquisition) {
        ThrottleAcquisitionState.IDLE -> DriveAcquisition.Idle
        ThrottleAcquisitionState.REQUESTED -> DriveAcquisition.Requested
        ThrottleAcquisitionState.ACQUIRED -> DriveAcquisition.Acquired
        ThrottleAcquisitionState.IN_USE -> DriveAcquisition.InUse
        ThrottleAcquisitionState.TAKING_CONTROL -> DriveAcquisition.TakingControl
        ThrottleAcquisitionState.RELEASING -> DriveAcquisition.Releasing
        ThrottleAcquisitionState.LOST -> DriveAcquisition.Lost
        ThrottleAcquisitionState.FAILED -> DriveAcquisition.Failed
    },
    canControl = canControl,
    emergencyStop = emergencyStop,
    issue = when (issue) {
        is ThrottleIssue.InUse -> DriveIssue.InUse
        is ThrottleIssue.AcquireTimedOut -> DriveIssue.AcquireTimedOut
        is ThrottleIssue.CommandFailed -> DriveIssue.CommandFailed
        null -> null
    },
)
