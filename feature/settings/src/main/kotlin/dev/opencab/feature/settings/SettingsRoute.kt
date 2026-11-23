package dev.opencab.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencab.core.data.settings.AppTheme
import dev.opencab.core.designsystem.component.CabScaffold
import dev.opencab.core.designsystem.component.SecondaryActionButton
import dev.opencab.core.designsystem.component.SectionCard
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.theme.CabTheme

const val SETTINGS_ROUTE: String = "settings"

@Composable
fun SettingsRoute(
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onThemeSelected = viewModel::setTheme,
        onOpenConnection = onOpenConnection,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeSelected: (AppTheme) -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CabScaffold(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_subtitle),
        modifier = modifier.testTag("screen_settings"),
    ) { contentPadding ->
        Box(
            modifier =
            Modifier
                .padding(contentPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                Modifier
                    .widthIn(max = CabTheme.sizing.contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CabTheme.spacing.page, vertical = CabTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.md),
            ) {
                AppearanceCard(
                    selectedTheme = uiState.theme,
                    onThemeSelected = onThemeSelected,
                )
                ConnectionCard(onOpenConnection = onOpenConnection)
                AboutCard()
            }
        }
    }
}

@Composable
private fun AppearanceCard(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.settings_appearance),
        subtitle = stringResource(R.string.settings_appearance_body),
    ) {
        ThemeOption(
            label = stringResource(R.string.settings_theme_system),
            selected = selectedTheme == AppTheme.SYSTEM,
            onClick = { onThemeSelected(AppTheme.SYSTEM) },
        )
        ThemeOption(
            label = stringResource(R.string.settings_theme_light),
            selected = selectedTheme == AppTheme.LIGHT,
            onClick = { onThemeSelected(AppTheme.LIGHT) },
        )
        ThemeOption(
            label = stringResource(R.string.settings_theme_dark),
            selected = selectedTheme == AppTheme.DARK,
            onClick = { onThemeSelected(AppTheme.DARK) },
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = CabTheme.sizing.touchMin)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ).padding(vertical = CabTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(Modifier.width(CabTheme.spacing.sm))
        Text(
            text = label,
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ConnectionCard(onOpenConnection: () -> Unit) {
    SectionCard(title = stringResource(R.string.settings_connection_title)) {
        Text(
            text = stringResource(R.string.settings_connection_body),
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryActionButton(
            text = stringResource(R.string.settings_open_connection),
            onClick = onOpenConnection,
            modifier = Modifier.testTag("settings_open_connection"),
        )
    }
}

@Composable
private fun AboutCard() {
    SectionCard(title = stringResource(R.string.settings_about_title)) {
        Text(
            text = stringResource(R.string.settings_about_body),
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@CabPreview
@Composable
private fun SettingsScreenPreview() {
    CabTheme {
        SettingsScreen(
            uiState = SettingsUiState(theme = AppTheme.SYSTEM),
            onThemeSelected = {},
            onOpenConnection = {},
        )
    }
}
