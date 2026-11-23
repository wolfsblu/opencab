package dev.opencab.feature.roster

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.opencab.core.data.roster.RosterRepository
import dev.opencab.core.data.roster.RosterSaveResult
import dev.opencab.core.data.roster.RosterValidationError
import dev.opencab.core.model.AddressValidation
import dev.opencab.core.model.AddressValidationError
import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterAddressRules
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.model.RosterColor
import dev.opencab.core.model.RosterIcon
import dev.opencab.core.model.RosterLoco
import dev.opencab.core.model.RosterLocoDraft
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class EditorSaveError {
    NAME_REQUIRED,
    ADDRESS_INVALID,
    NOT_FOUND,
}

data class LocoEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val address: String = "",
    val type: RosterAddressType? = null,
    val isFavorite: Boolean = false,
    val color: RosterColor = RosterColor.BLUE,
    val icon: RosterIcon = RosterIcon.STEAM,
    val functionLabels: Map<Int, String> = emptyMap(),
    val serverFunctionLabels: Map<Int, String> = emptyMap(),
    val validation: AddressValidation = AddressValidation.Invalid(AddressValidationError.REQUIRED),
    val showErrors: Boolean = false,
    val saveError: EditorSaveError? = null,
    val duplicateName: String? = null,
    val isSaving: Boolean = false,
    val addressChanged: Boolean = false,
) {
    val canSave: Boolean
        get() = !isLoading && !isSaving
}

sealed interface EditorEvent {
    data class Saved(
        val locoId: String,
        val createdNewEntry: Boolean,
    ) : EditorEvent
}

@HiltViewModel
class LocoEditorViewModel @Inject constructor(
    private val repository: RosterRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val locoId: String? = savedStateHandle.get<String>(ROSTER_LOCO_ID_ARG)?.takeIf { it.isNotBlank() }
    private var original: RosterLoco? = null
    private val mutableState = MutableStateFlow(
        LocoEditorUiState(isLoading = locoId != null, isNew = locoId == null),
    )
    val uiState: StateFlow<LocoEditorUiState> = mutableState.asStateFlow()
    private val events = Channel<EditorEvent>(capacity = Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val actions: LocoEditorActions = object : LocoEditorActions {
        override fun onNameChange(value: String) = this@LocoEditorViewModel.onNameChange(value)

        override fun onAddressChange(value: String) = this@LocoEditorViewModel.onAddressChange(value)

        override fun onTypeChange(type: RosterAddressType?) = this@LocoEditorViewModel.onTypeChange(type)

        override fun onFavoriteChange(value: Boolean) = this@LocoEditorViewModel.onFavoriteChange(value)

        override fun onColorChange(color: RosterColor) = this@LocoEditorViewModel.onColorChange(color)

        override fun onIconChange(icon: RosterIcon) = this@LocoEditorViewModel.onIconChange(icon)

        override fun onFunctionLabelChange(
            number: Int,
            value: String,
        ) = this@LocoEditorViewModel.onFunctionLabelChange(number, value)

        override fun onSave() = this@LocoEditorViewModel.onSave()
    }

    init {
        if (locoId == null) {
            mutableState.value = mutableState.value.copy(isLoading = false)
        } else {
            viewModelScope.launch {
                val loco = repository.loco(locoId)
                if (loco == null) {
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        saveError = EditorSaveError.NOT_FOUND,
                    )
                } else {
                    original = loco
                    mutableState.value = loco.toEditorState()
                }
            }
        }
    }

    fun onNameChange(value: String) {
        update { copy(name = value, saveError = null, duplicateName = null) }
    }

    fun onAddressChange(value: String) {
        update {
            copy(
                address = value.filter(Char::isDigit),
                saveError = null,
                duplicateName = null,
            )
        }
    }

    fun onTypeChange(type: RosterAddressType?) {
        update { copy(type = type, saveError = null, duplicateName = null) }
    }

    fun onFavoriteChange(value: Boolean) {
        update { copy(isFavorite = value) }
    }

    fun onColorChange(color: RosterColor) {
        update { copy(color = color) }
    }

    fun onIconChange(icon: RosterIcon) {
        update { copy(icon = icon) }
    }

    fun onFunctionLabelChange(
        number: Int,
        value: String,
    ) {
        update {
            val labels = functionLabels.toMutableMap()
            if (value.isBlank()) labels.remove(number) else labels[number] = value
            copy(functionLabels = labels)
        }
    }

    fun onSave() {
        val current = mutableState.value
        if (current.isSaving || current.isLoading) return
        val validation = RosterAddressRules.validate(current.address, current.type)
        if (current.name.isBlank()) {
            update { copy(showErrors = true, saveError = EditorSaveError.NAME_REQUIRED) }
            return
        }
        if (validation !is AddressValidation.Valid) {
            update {
                copy(
                    showErrors = true,
                    validation = validation,
                    saveError = EditorSaveError.ADDRESS_INVALID,
                )
            }
            return
        }
        update { copy(isSaving = true, saveError = null, duplicateName = null) }
        val draft = current.toDraft(validation.address, original?.serverRosterId)
        viewModelScope.launch {
            val result = if (locoId == null) {
                repository.create(draft)
            } else {
                repository.update(locoId, draft)
            }
            when (result) {
                is RosterSaveResult.Saved -> {
                    update { copy(isSaving = false) }
                    events.send(
                        EditorEvent.Saved(
                            locoId = result.loco.id,
                            createdNewEntry = result.createdNewEntry,
                        ),
                    )
                }

                is RosterSaveResult.DuplicateAddress -> update {
                    copy(
                        isSaving = false,
                        saveError = EditorSaveError.ADDRESS_INVALID,
                        duplicateName = result.existing.displayName,
                    )
                }

                is RosterSaveResult.Invalid -> update {
                    copy(
                        isSaving = false,
                        saveError = when (result.reason) {
                            RosterValidationError.NAME_REQUIRED -> EditorSaveError.NAME_REQUIRED
                            RosterValidationError.NOT_FOUND -> EditorSaveError.NOT_FOUND
                        },
                    )
                }
            }
        }
    }

    private fun update(transform: LocoEditorUiState.() -> LocoEditorUiState) {
        mutableState.value = mutableState.value.withDerived().transform().withDerived()
    }

    private fun LocoEditorUiState.withDerived(): LocoEditorUiState {
        val validation = RosterAddressRules.validate(address, type)
        val addressChanged = original?.let {
            it.address != validation.address && validation.address != null
        } ?: false
        return copy(validation = validation, addressChanged = addressChanged)
    }
}

private fun RosterLoco.toEditorState(): LocoEditorUiState = LocoEditorUiState(
    isLoading = false,
    isNew = false,
    name = name,
    address = address.number.toString(),
    type = address.type,
    isFavorite = isFavorite,
    color = color,
    icon = icon,
    functionLabels = localFunctionLabels,
    serverFunctionLabels = serverFunctionLabels,
    validation = AddressValidation.Valid(address, isAmbiguous = false),
    addressChanged = false,
)

private fun LocoEditorUiState.toDraft(
    address: RosterAddress,
    serverRosterId: String?,
): RosterLocoDraft = RosterLocoDraft(
    name = name.trim(),
    address = address,
    localFunctionLabels = functionLabels.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() },
    isFavorite = isFavorite,
    color = color,
    icon = icon,
    serverRosterId = serverRosterId,
)
