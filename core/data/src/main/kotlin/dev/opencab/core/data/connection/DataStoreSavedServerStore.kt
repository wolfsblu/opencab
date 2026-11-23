package dev.opencab.core.data.connection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.opencab.core.model.SavedServer
import dev.opencab.core.model.ServerEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.connectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "connection",
)

@Singleton
class DataStoreSavedServerStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SavedServerStore {
    override val servers: Flow<List<SavedServer>> =
        context.connectionDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }.map { preferences ->
                SavedServerCodec.decode(preferences[savedServersKey].orEmpty())
            }

    override suspend fun upsert(server: SavedServer) {
        context.connectionDataStore.edit { preferences ->
            val current = SavedServerCodec.decode(preferences[savedServersKey].orEmpty())
            val withoutDuplicates = current.filterNot {
                it.id == server.id || it.endpoint == server.endpoint
            }
            preferences[savedServersKey] = SavedServerCodec.encode(
                (withoutDuplicates + server).sortedBy { it.name.lowercase() },
            )
        }
    }

    override suspend fun delete(id: String) {
        context.connectionDataStore.edit { preferences ->
            val current = SavedServerCodec.decode(preferences[savedServersKey].orEmpty())
            preferences[savedServersKey] = SavedServerCodec.encode(current.filterNot { it.id == id })
        }
    }

    override suspend fun recordConnected(
        name: String,
        endpoint: ServerEndpoint,
        nowEpochMillis: Long,
    ): SavedServer {
        val existing = servers.first().firstOrNull { it.endpoint == endpoint }
        val server = existing?.copy(lastConnectedEpochMillis = nowEpochMillis)
            ?: SavedServer.create(
                name = name,
                endpoint = endpoint,
                lastConnectedEpochMillis = nowEpochMillis,
            )
        upsert(server)
        return server
    }

    private companion object {
        val savedServersKey = stringPreferencesKey("saved_servers")
    }
}
