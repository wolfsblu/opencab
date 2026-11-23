package dev.opencab.feature.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.core.model.AddressValidation
import dev.opencab.core.model.AddressValidationError
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.model.RosterColor
import dev.opencab.core.model.RosterIcon

@Composable
internal fun LocoBadge(
    color: RosterColor,
    icon: RosterIcon,
    modifier: Modifier = Modifier,
) {
    val tint = color.toComposeColor()
    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = tint.copy(alpha = 0.16f),
        contentColor = tint,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon.imageVector(),
                contentDescription = null,
                modifier = Modifier.size(CabTheme.sizing.iconMedium),
                tint = tint,
            )
        }
    }
}

@Composable
internal fun ActiveChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CabTheme.shapes.pill,
        color = CabTheme.colorScheme.primaryContainer,
        contentColor = CabTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.roster_active),
            style = CabTheme.typography.status,
            modifier = Modifier.padding(horizontal = CabTheme.spacing.sm, vertical = 4.dp),
        )
    }
}

@Composable
internal fun addressErrorMessage(error: AddressValidationError): String = stringResource(
    when (error) {
        AddressValidationError.REQUIRED -> R.string.picker_invalid_required
        AddressValidationError.NOT_A_NUMBER -> R.string.picker_invalid_number
        AddressValidationError.OUT_OF_RANGE -> R.string.picker_invalid_out_of_range
        AddressValidationError.SHORT_OUT_OF_RANGE -> R.string.picker_invalid_short
        AddressValidationError.LONG_OUT_OF_RANGE -> R.string.picker_invalid_long
    },
)

/** Supporting copy shown under every address field. */
@Composable
internal fun addressSupportingText(
    validation: AddressValidation,
    showError: Boolean,
): String {
    if (showError && validation is AddressValidation.Invalid) {
        return addressErrorMessage(validation.error)
    }
    return when (validation) {
        is AddressValidation.Valid ->
            if (validation.isAmbiguous) {
                if (validation.address.type == RosterAddressType.LONG) {
                    stringResource(R.string.picker_ambiguous_long)
                } else {
                    stringResource(R.string.picker_ambiguous)
                }
            } else if (validation.address.type == RosterAddressType.LONG) {
                stringResource(R.string.picker_using_long)
            } else {
                stringResource(R.string.picker_using_short)
            }

        is AddressValidation.Invalid -> stringResource(R.string.picker_address_hint)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddressTypeChips(
    selected: RosterAddressType?,
    onSelect: (RosterAddressType?) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.editor_type_auto)) },
            modifier = Modifier.testTag(RosterTestTags.addressType("AUTO")),
        )
        FilterChip(
            selected = selected == RosterAddressType.SHORT,
            onClick = { onSelect(RosterAddressType.SHORT) },
            label = { Text(stringResource(R.string.editor_type_short)) },
            modifier = Modifier.testTag(RosterTestTags.addressType("SHORT")),
        )
        FilterChip(
            selected = selected == RosterAddressType.LONG,
            onClick = { onSelect(RosterAddressType.LONG) },
            label = { Text(stringResource(R.string.editor_type_long)) },
            modifier = Modifier.testTag(RosterTestTags.addressType("LONG")),
        )
    }
}
