package dev.opencab.feature.throttle

import androidx.compose.runtime.Composable
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.theme.CabTheme

/** Deterministic states used by previews and screenshot tooling. */
internal object DrivePreviewData {
    fun connected(): DriveUiState = DriveUiState(
        layoutName = "Junction Yard",
        link = DriveLink.Connected,
        loco = loco(),
        scale = DriveScale(maxScaleSpeed = 128),
    )

    fun reconnecting(): DriveUiState = DriveUiState(
        layoutName = "Junction Yard",
        link = DriveLink.Reconnecting,
        reconnectAttempt = 3,
        loco = loco(
            speedStep = 62,
            health = DriveHealth.Stale,
            canControl = false,
        ),
    )

    fun stale(): DriveUiState = DriveUiState(
        layoutName = "Junction Yard",
        link = DriveLink.Connected,
        loco = loco(
            speedStep = 88,
            health = DriveHealth.Stale,
            canControl = false,
        ),
    )

    fun inUse(): DriveUiState = DriveUiState(
        layoutName = "Junction Yard",
        link = DriveLink.Connected,
        loco = loco(
            acquisition = DriveAcquisition.InUse,
            canControl = false,
            issue = DriveIssue.InUse,
        ),
    )

    fun maxSpeed(): DriveUiState = DriveUiState(
        layoutName = "Junction Yard",
        link = DriveLink.Connected,
        loco = loco(speedStep = 128, speedSteps = 128),
        scale = DriveScale(maxScaleSpeed = 160, unit = DriveSpeedUnit.MilesPerHour),
    )

    fun activeFunctions(): DriveUiState = DriveUiState(
        layoutName = "Junction Yard",
        link = DriveLink.Connected,
        loco = loco(
            speedStep = 24,
            functions = functions(active = setOf(0, 1, 2, 4, 6, 8, 12, 18)),
        ),
    )

    fun longLabels(): DriveUiState = DriveUiState(
        layoutName = "The North Western Junction Preservation Society",
        link = DriveLink.Connected,
        loco = loco(
            name = "LMS Stanier Class 5 4-6-0 Black Five 45305",
            address = "45305",
            speedStep = 51,
            functions = functions(
                labels = buildMap {
                    put(0, "Headlight and marker lights")
                    put(1, "Whistle — long, low, mournful")
                    put(2, "Coal pusher screw")
                    put(3, "Firebox door")
                },
            ),
        ),
    )

    fun noLocomotive(): DriveUiState = DriveUiState(
        layoutName = "Junction Yard",
        link = DriveLink.Connected,
    )

    @Suppress("LongParameterList")
    private fun loco(
        name: String = "3412 Black Five",
        address: String = "3412",
        speedSteps: Int = 128,
        speedStep: Int = 42,
        direction: DriveDirection = DriveDirection.Forward,
        health: DriveHealth = DriveHealth.Online,
        acquisition: DriveAcquisition = DriveAcquisition.Acquired,
        canControl: Boolean = true,
        issue: DriveIssue? = null,
        functions: List<DriveFunction> = functions(),
    ): DriveLoco = DriveLoco(
        address = address,
        name = name,
        speedStepCount = speedSteps,
        speedStep = speedStep,
        speed = speedStep.toFloat() / speedSteps,
        direction = direction,
        functions = functions,
        health = health,
        acquisition = acquisition,
        canControl = canControl,
        issue = issue,
    )

    private fun functions(
        active: Set<Int> = setOf(0, 2),
        labels: Map<Int, String> = emptyMap(),
    ): List<DriveFunction> = (0..28).map { number ->
        DriveFunction(
            number = number,
            label = labels[number] ?: defaultLabels[number] ?: "F$number",
            isOn = number in active,
            isMomentary = number == 2 || number == 3,
            isEnabled = true,
        )
    }

    private val defaultLabels: Map<Int, String> = buildMap {
        put(0, "Headlight")
        put(1, "Whistle")
        put(2, "Bell")
        put(3, "Coupler")
        put(4, "Steam")
        put(5, "Horn")
        put(6, "Cab light")
        put(7, "Marker")
        put(8, "Ditch lights")
        put(9, "Firebox")
        put(10, "Blower")
        put(11, "Injector")
    }
}

@CabPreview
@Composable
private fun DriveConnectedPreview() {
    CabTheme {
        DriveScreen(uiState = DrivePreviewData.connected())
    }
}

@CabPreview
@Composable
private fun DriveReconnectingPreview() {
    CabTheme {
        DriveScreen(uiState = DrivePreviewData.reconnecting())
    }
}

@CabPreview
@Composable
private fun DriveStalePreview() {
    CabTheme {
        DriveScreen(uiState = DrivePreviewData.stale())
    }
}

@CabPreview
@Composable
private fun DriveInUsePreview() {
    CabTheme {
        DriveScreen(uiState = DrivePreviewData.inUse())
    }
}

@CabPreview
@Composable
private fun DriveMaxSpeedPreview() {
    CabTheme {
        DriveScreen(uiState = DrivePreviewData.maxSpeed())
    }
}

@CabPreview
@Composable
private fun DriveActiveFunctionsPreview() {
    CabTheme {
        DriveScreen(uiState = DrivePreviewData.activeFunctions())
    }
}

@CabPreview
@Composable
private fun DriveLongLabelsPreview() {
    CabTheme {
        DriveScreen(uiState = DrivePreviewData.longLabels())
    }
}

@CabPreview
@Composable
private fun DriveNoLocomotivePreview() {
    CabTheme {
        DriveScreen(uiState = DrivePreviewData.noLocomotive())
    }
}
