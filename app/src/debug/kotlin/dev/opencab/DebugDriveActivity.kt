package dev.opencab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.feature.throttle.DriveDebugPreview

/**
 * Debug-only host for the drive-screen screenshot loop.
 *
 * It is declared only in the debug manifest, so it is absent from release builds. Launch it with
 * `adb shell am start -n dev.opencab/.DebugDriveActivity --es state reconnecting`.
 */
class DebugDriveActivity : ComponentActivity() {
    private var previewState by mutableStateOf(DEFAULT_STATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        previewState = readState(intent)
        setContent {
            CabTheme(darkTheme = isSystemInDarkTheme()) {
                DriveDebugPreview(state = previewState)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        previewState = readState(intent)
    }

    private fun readState(intent: Intent?): String = intent?.getStringExtra(EXTRA_STATE) ?: DEFAULT_STATE

    private companion object {
        const val EXTRA_STATE = "state"
        const val DEFAULT_STATE = "connected"
    }
}
