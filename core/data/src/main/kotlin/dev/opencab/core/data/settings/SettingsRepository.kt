package dev.opencab.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

interface SettingsRepository {
    val appTheme: Flow<AppTheme>

    suspend fun setAppTheme(theme: AppTheme)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

class DataStoreSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SettingsRepository {
    override val appTheme: Flow<AppTheme> =
        context.settingsDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }.map { preferences ->
                val storedTheme = preferences[themeKey]
                AppTheme.entries.firstOrNull { it.name == storedTheme } ?: AppTheme.SYSTEM
            }

    override suspend fun setAppTheme(theme: AppTheme) {
        context.settingsDataStore.edit { preferences ->
            preferences[themeKey] = theme.name
        }
    }

    private companion object {
        val themeKey = stringPreferencesKey("app_theme")
    }
}
