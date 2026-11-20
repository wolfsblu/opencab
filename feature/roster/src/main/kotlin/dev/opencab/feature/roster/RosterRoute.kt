package dev.opencab.feature.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.opencab.core.designsystem.component.CabScaffold
import dev.opencab.core.designsystem.component.EmptyState
import dev.opencab.core.designsystem.component.SectionCard
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.theme.CabTheme

const val ROSTER_ROUTE: String = "roster"

@Composable
fun RosterRoute(modifier: Modifier = Modifier) {
    CabScaffold(
        title = stringResource(R.string.roster_title),
        modifier = modifier.testTag("screen_roster"),
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
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.roster_empty_title),
                    message = stringResource(R.string.roster_empty_body),
                )
                SectionCard(title = stringResource(R.string.roster_upcoming_title)) {
                    Text(
                        text = stringResource(R.string.roster_upcoming_body),
                        style = CabTheme.typography.readout,
                        color = CabTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@CabPreview
@Composable
private fun RosterScreenPreview() {
    CabTheme {
        RosterRoute()
    }
}
