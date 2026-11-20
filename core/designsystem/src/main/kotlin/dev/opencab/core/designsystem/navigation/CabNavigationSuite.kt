package dev.opencab.core.designsystem.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.window.core.layout.WindowSizeClass

@Immutable
data class CabNavigationDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String? = null,
)

/**
 * The adaptive navigation shell.
 *
 * Compact windows get a bottom navigation bar; medium and expanded windows get a navigation
 * rail. The suite consumes the navigation-bar or rail insets itself, so screen scaffolds
 * should not add them again.
 *
 * Set [visible] to false for destinations outside the top-level graph; the suite animates out
 * and the content keeps the full window. The surrounding app is responsible for system back
 * handling; this component never intercepts it.
 */
@Composable
fun CabNavigationSuite(
    destinations: List<CabNavigationDestination>,
    selectedRoute: String?,
    onNavigate: (CabNavigationDestination) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isAtLeastMedium =
        currentWindowAdaptiveInfo()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val navigationSuiteType =
        when {
            !visible -> NavigationSuiteType.None
            isAtLeastMedium -> NavigationSuiteType.WideNavigationRailCollapsed
            else -> NavigationSuiteType.ShortNavigationBarCompact
        }

    NavigationSuiteScaffold(
        navigationItems = {
            destinations.forEach { destination ->
                NavigationSuiteItem(
                    selected = destination.route == selectedRoute,
                    onClick = { onNavigate(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(destination.label) },
                    navigationSuiteType = navigationSuiteType,
                    modifier =
                    if (destination.testTag != null) {
                        Modifier.testTag(destination.testTag)
                    } else {
                        Modifier
                    },
                )
            }
        },
        modifier = modifier,
        navigationSuiteType = navigationSuiteType,
        content = content,
    )
}
