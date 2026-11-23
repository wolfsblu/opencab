package dev.opencab.feature.roster

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.opencab.core.designsystem.component.CabScaffold
import dev.opencab.core.designsystem.component.EmptyState
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
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.roster_empty_title),
                message = stringResource(R.string.roster_empty_body),
            )
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
