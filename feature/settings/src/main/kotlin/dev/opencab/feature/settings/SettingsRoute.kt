package dev.opencab.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencab.core.data.settings.AppTheme
import dev.opencab.core.designsystem.component.OpenCabScaffold
import dev.opencab.core.designsystem.theme.OpenCabTheme

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
    OpenCabScaffold(
        title = stringResource(R.string.settings_title),
        modifier = modifier.testTag("screen_settings"),
    ) { contentPadding ->
        val contentModifier =
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)

        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

@Composable
private fun AppearanceCard(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
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
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ConnectionCard(onOpenConnection: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_connection_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_connection_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onOpenConnection,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.settings_open_connection))
            }
        }
    }
}

@Composable
private fun AboutCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_about_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAFF)
@Composable
internal fun SettingsScreenPreview() {
    OpenCabTheme(dynamicColor = false) {
        SettingsScreen(
            uiState = SettingsUiState(theme = AppTheme.SYSTEM),
            onThemeSelected = {},
            onOpenConnection = {},
        )
    }
}
