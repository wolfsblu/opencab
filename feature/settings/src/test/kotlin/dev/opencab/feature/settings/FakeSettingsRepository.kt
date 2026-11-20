package dev.opencab.feature.settings

import dev.opencab.core.data.settings.AppTheme
import dev.opencab.core.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeSettingsRepository : SettingsRepository {
    private val theme = MutableStateFlow(AppTheme.SYSTEM)

    override val appTheme: Flow<AppTheme> = theme

    override suspend fun setAppTheme(theme: AppTheme) {
        this.theme.value = theme
    }
}
