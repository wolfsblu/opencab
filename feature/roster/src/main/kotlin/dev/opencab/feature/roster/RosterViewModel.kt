package dev.opencab.feature.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.opencab.core.data.roster.RosterRepository
import dev.opencab.core.data.throttle.ThrottleUiSource
import dev.opencab.core.model.RosterLoco
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RosterViewModel @Inject constructor(
    private val repository: RosterRepository,
    private val acquirer: LocoAcquirer,
    private val session: ThrottleUiSource,
) : ViewModel() {
    private val importedCount = MutableStateFlow<Int?>(null)
    private val locos: StateFlow<List<RosterLoco>> = repository.locos.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val events = Channel<RosterEvent>(capacity = Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val uiState: StateFlow<RosterUiState> = combine(
        locos,
        session.uiState,
        importedCount,
    ) { roster, throttle, imported ->
        val activeKey = throttle.slots.lastOrNull { it.desired }?.addressKey
        RosterUiState(
            isLoading = false,
            locos = roster.toRosterItems(activeKey),
            serverRosterCount = throttle.serverRoster.size,
            importedCount = imported,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RosterUiState(),
    )

    fun onDrive(locoId: String) {
        viewModelScope.launch {
            val loco = locos.value.firstOrNull { it.id == locoId }
                ?: repository.loco(locoId)
                ?: return@launch
            acquirer.acquire(loco)
            repository.markUsed(loco.id)
            events.send(RosterEvent.OpenDrive)
        }
    }

    fun onImportServerRoster() {
        if (!RosterFeatureFlags.JMRI_ROSTER_IMPORT_ENABLED) return
        viewModelScope.launch {
            val added = repository.importServerRoster(session.uiState.value.serverRoster)
            importedCount.value = added
        }
    }
}
