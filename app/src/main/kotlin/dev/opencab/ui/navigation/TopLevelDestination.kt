package dev.opencab.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.opencab.R
import dev.opencab.feature.roster.ROSTER_ROUTE
import dev.opencab.feature.settings.SETTINGS_ROUTE
import dev.opencab.feature.throttle.DRIVE_ROUTE
import dev.opencab.ui.OpenCabTestTags

internal data class TopLevelDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val testTag: String,
)

internal val topLevelDestinations: List<TopLevelDestination> =
    listOf(
        TopLevelDestination(
            route = DRIVE_ROUTE,
            labelRes = R.string.nav_drive,
            icon = Icons.Default.PlayArrow,
            testTag = OpenCabTestTags.NAV_DRIVE,
        ),
        TopLevelDestination(
            route = ROSTER_ROUTE,
            labelRes = R.string.nav_roster,
            icon = Icons.AutoMirrored.Filled.List,
            testTag = OpenCabTestTags.NAV_ROSTER,
        ),
        TopLevelDestination(
            route = SETTINGS_ROUTE,
            labelRes = R.string.nav_settings,
            icon = Icons.Default.Settings,
            testTag = OpenCabTestTags.NAV_SETTINGS,
        ),
    )
