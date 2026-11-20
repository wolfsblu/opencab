package dev.opencab.feature.settings

import app.cash.turbine.test
import dev.opencab.core.data.settings.AppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `ui state follows the stored theme and theme changes`() = runTest {
        val viewModel = SettingsViewModel(FakeSettingsRepository())

        viewModel.uiState.test {
            assertEquals(AppTheme.SYSTEM, awaitItem().theme)

            viewModel.setTheme(AppTheme.DARK)

            assertEquals(AppTheme.DARK, awaitItem().theme)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
