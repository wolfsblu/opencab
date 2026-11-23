package dev.opencab.feature.throttle

import androidx.compose.runtime.Composable
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * Debug-only bridge between the screenshot loop and [DrivePreviewData].
 *
 * This lives in the debug source set so no preview entry point or demo state can be reached
 * from a release build. Previews in the main source set still render the same data.
 */
@Composable
fun DriveDebugPreview(state: String = "connected") {
    CabTheme {
        DriveScreen(
            uiState = DriveDebugPreviewData.state(state),
            initialTab =
            if (state == "functions") {
                DriveInstrumentTab.Functions
            } else {
                DriveInstrumentTab.Throttle
            },
        )
    }
}

/** Named debug states, intentionally small and stable for adb-driven screenshots. */
object DriveDebugPreviewData {
    val names: List<String> =
        listOf(
            "connected",
            "stopped",
            "reconnecting",
            "stale",
            "in-use",
            "max",
            "active-functions",
            "functions",
            "long-labels",
            "empty",
        )

    fun state(name: String): DriveUiState = when (name) {
        "stopped" -> DrivePreviewData.stopped()
        "reconnecting" -> DrivePreviewData.reconnecting()
        "stale" -> DrivePreviewData.stale()
        "in-use" -> DrivePreviewData.inUse()
        "max" -> DrivePreviewData.maxSpeed()
        "active-functions" -> DrivePreviewData.activeFunctions()
        "functions" -> DrivePreviewData.connected()
        "long-labels" -> DrivePreviewData.longLabels()
        "empty" -> DrivePreviewData.noLocomotive()
        else -> DrivePreviewData.connected()
    }
}
