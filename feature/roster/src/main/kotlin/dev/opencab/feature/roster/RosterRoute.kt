package dev.opencab.feature.roster

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.component.EmptyState
import dev.opencab.core.designsystem.component.OpenCabScaffold
import dev.opencab.core.designsystem.theme.OpenCabTheme

const val ROSTER_ROUTE: String = "roster"

@Composable
fun RosterRoute(modifier: Modifier = Modifier) {
    OpenCabScaffold(
        title = stringResource(R.string.roster_title),
        modifier = modifier.testTag("screen_roster"),
    ) { contentPadding ->
        Column(
            modifier =
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.roster_empty_title),
                message = stringResource(R.string.roster_empty_body),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAFF)
@Composable
internal fun RosterScreenPreview() {
    OpenCabTheme(dynamicColor = false) {
        RosterRoute()
    }
}
