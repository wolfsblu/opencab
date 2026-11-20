package dev.opencab.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.opencab.feature.connection.CONNECTION_ROUTE
import dev.opencab.feature.connection.ConnectionRoute
import dev.opencab.feature.roster.ROSTER_ROUTE
import dev.opencab.feature.roster.RosterRoute
import dev.opencab.feature.settings.SETTINGS_ROUTE
import dev.opencab.feature.settings.SettingsRoute
import dev.opencab.feature.throttle.DRIVE_ROUTE
import dev.opencab.feature.throttle.DriveRoute
import dev.opencab.ui.navigation.topLevelDestinations

@Composable
fun OpenCabApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelDestination =
        currentRoute == null || topLevelDestinations.any { destination -> destination.route == currentRoute }
    val navigationSuiteType =
        if (isTopLevelDestination) {
            NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
        } else {
            NavigationSuiteType.None
        }

    NavigationSuiteScaffold(
        navigationItems = {
            topLevelDestinations.forEach { destination ->
                NavigationSuiteItem(
                    selected = currentRoute == destination.route,
                    onClick = { navController.navigateToTopLevel(destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                    modifier = Modifier.testTag(destination.testTag),
                )
            }
        },
        navigationSuiteType = navigationSuiteType,
        modifier = modifier,
    ) {
        NavHost(
            navController = navController,
            startDestination = DRIVE_ROUTE,
        ) {
            composable(DRIVE_ROUTE) {
                DriveRoute(
                    onOpenConnection = { navController.navigate(CONNECTION_ROUTE) },
                )
            }
            composable(ROSTER_ROUTE) {
                RosterRoute()
            }
            composable(SETTINGS_ROUTE) {
                SettingsRoute(
                    onOpenConnection = { navController.navigate(CONNECTION_ROUTE) },
                )
            }
            composable(CONNECTION_ROUTE) {
                ConnectionRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
