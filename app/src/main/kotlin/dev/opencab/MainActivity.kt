package dev.opencab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.opencab.core.data.settings.AppTheme
import dev.opencab.core.data.settings.SettingsRepository
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.ui.OpenCabApp
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appTheme by settingsRepository.appTheme.collectAsStateWithLifecycle(
                initialValue = AppTheme.SYSTEM,
            )
            val darkTheme =
                when (appTheme) {
                    AppTheme.SYSTEM -> isSystemInDarkTheme()
                    AppTheme.LIGHT -> false
                    AppTheme.DARK -> true
                }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }

            CabTheme(darkTheme = darkTheme) {
                OpenCabApp(onExit = { finish() })
            }
        }
    }
}
