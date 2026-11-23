package dev.opencab.feature.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.opencab.core.data.roster.RosterRepository
import dev.opencab.core.data.roster.RosterSaveResult
import dev.opencab.core.data.throttle.PrimaryAcquireResult
import dev.opencab.core.data.throttle.ThrottleUiSource
import dev.opencab.core.model.AddressValidation
import dev.opencab.core.model.AddressValidationError
import dev.opencab.core.model.RosterAddressRules
import dev.opencab.core.model.RosterAddressType
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
class LocoPickerViewModel @Inject constructor(
    private val repository: RosterRepository,
    private val acquirer: LocoAcquirer,
    session: ThrottleUiSource,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val mode = MutableStateFlow(PickerMode.List)
    private val form = MutableStateFlow(PickerAddForm())
    private val addError = MutableStateFlow<PickerAddError?>(null)
    private val saving = MutableStateFlow(false)

    private val rosterAndThrottle = combine(repository.locos, session.uiState) { locos, throttle ->
        val activeKey = throttle.slots.lastOrNull { it.desired }?.addressKey
        locos to throttle.serverRoster.size to activeKey
    }
    private val controls = combine(query, mode, form, addError, saving) { query, mode, form, error, saving ->
        PickerControls(query = query, mode = mode, form = form, addError = error, isSaving = saving)
    }

    val uiState: StateFlow<LocoPickerUiState> = combine(rosterAndThrottle, controls) { roster, controls ->
        val (locos, serverRosterCount) = roster.first
        val activeKey = roster.second
        val validation = RosterAddressRules.validate(controls.form.address, controls.form.type)
        LocoPickerUiState(
            query = controls.query,
            mode = controls.mode,
            allLocos = locos,
            sections = RosterSearch.sections(locos, controls.query),
            activeLocoId = locos.firstOrNull { it.address.wireText == activeKey }?.id,
            form = controls.form,
            validation = validation,
            addError = controls.addError,
            isSaving = controls.isSaving,
            serverRosterCount = serverRosterCount,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocoPickerUiState(),
    )

    private val events = Channel<PickerResult>(capacity = Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val actions: LocoPickerActions = object : LocoPickerActions {
        override fun onQueryChange(value: String) = this@LocoPickerViewModel.onQueryChange(value)

        override fun onSelect(loco: RosterLoco) = this@LocoPickerViewModel.onSelect(loco)

        override fun onStartAdd(prefillAddress: String?) = this@LocoPickerViewModel.onStartAdd(prefillAddress)

        override fun onCancelAdd() = this@LocoPickerViewModel.onCancelAdd()

        override fun onAddNameChange(value: String) = this@LocoPickerViewModel.onAddNameChange(value)

        override fun onAddAddressChange(value: String) = this@LocoPickerViewModel.onAddAddressChange(value)

        override fun onAddTypeChange(type: RosterAddressType?) = this@LocoPickerViewModel.onAddTypeChange(type)

        override fun onSubmitAdd() = this@LocoPickerViewModel.onSubmitAdd()

        override fun onUseExisting() = this@LocoPickerViewModel.onUseExisting()

        override fun onDismiss() = Unit
    }

    fun reset() {
        query.value = ""
        mode.value = PickerMode.List
        form.value = PickerAddForm()
        addError.value = null
        saving.value = false
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onStartAdd(prefillAddress: String? = null) {
        addError.value = null
        val prefill = prefillAddress?.trim().orEmpty()
        val isAddress = RosterAddressRules.validate(prefill).address != null
        form.value = PickerAddForm(
            name = prefill.takeIf { it.isNotEmpty() && !isAddress }.orEmpty(),
            address = prefill.takeIf { isAddress }.orEmpty(),
        )
        mode.value = PickerMode.AddAddress
    }

    fun onCancelAdd() {
        addError.value = null
        mode.value = PickerMode.List
    }

    fun onAddNameChange(value: String) {
        form.value = form.value.copy(name = value)
        addError.value = null
    }

    fun onAddAddressChange(value: String) {
        form.value = form.value.copy(address = value.filter(Char::isDigit))
        addError.value = null
    }

    fun onAddTypeChange(type: RosterAddressType?) {
        form.value = form.value.copy(type = type)
        addError.value = null
    }

    fun onSelect(loco: RosterLoco) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            val result = acquirer.acquire(loco)
            repository.markUsed(loco.id)
            saving.value = false
            events.send(
                PickerResult(
                    locoId = loco.id,
                    wasAlreadyActive = result == PrimaryAcquireResult.ALREADY_ACTIVE,
                ),
            )
        }
    }

    fun onUseExisting() {
        val conflict = addError.value as? PickerAddError.Duplicate ?: return
        onSelect(conflict.existing)
    }

    fun onSubmitAdd() {
        if (saving.value) return
        val form = form.value
        val validation = RosterAddressRules.validate(form.address, form.type)
        if (validation !is AddressValidation.Valid) {
            val error = validation.error ?: return
            addError.value = PickerAddError.InvalidAddress(error)
            return
        }
        saving.value = true
        viewModelScope.launch {
            when (val result = repository.create(form.toDraft()!!)) {
                is RosterSaveResult.Saved -> {
                    val acquisition = acquirer.acquire(result.loco)
                    repository.markUsed(result.loco.id)
                    saving.value = false
                    events.send(
                        PickerResult(
                            locoId = result.loco.id,
                            wasAlreadyActive = acquisition == PrimaryAcquireResult.ALREADY_ACTIVE,
                        ),
                    )
                }

                is RosterSaveResult.DuplicateAddress -> {
                    saving.value = false
                    addError.value = PickerAddError.Duplicate(result.existing)
                }

                is RosterSaveResult.Invalid -> {
                    saving.value = false
                    addError.value = PickerAddError.InvalidAddress(
                        AddressValidationError.NOT_A_NUMBER,
                    )
                }
            }
        }
    }

    private data class PickerControls(
        val query: String,
        val mode: PickerMode,
        val form: PickerAddForm,
        val addError: PickerAddError?,
        val isSaving: Boolean,
    )
}

/** The picker's action surface, kept small so the sheet composable takes one callback group. */
@Suppress("TooManyFunctions")
interface LocoPickerActions {
    fun onQueryChange(value: String) = Unit

    fun onSelect(loco: RosterLoco) = Unit

    fun onStartAdd(prefillAddress: String?) = Unit

    fun onCancelAdd() = Unit

    fun onAddNameChange(value: String) = Unit

    fun onAddAddressChange(value: String) = Unit

    fun onAddTypeChange(type: RosterAddressType?) = Unit

    fun onSubmitAdd() = Unit

    fun onUseExisting() = Unit

    fun onDismiss() = Unit

    companion object {
        val None: LocoPickerActions = object : LocoPickerActions {}
    }
}
