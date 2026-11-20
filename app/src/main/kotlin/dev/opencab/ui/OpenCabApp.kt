package dev.opencab.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.opencab.core.designsystem.navigation.CabNavigationDestination
import dev.opencab.core.designsystem.navigation.CabNavigationSuite
import dev.opencab.core.designsystem.navigation.CabPredictiveBackContainer
import dev.opencab.core.designsystem.theme.CabTheme
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
    val navigationDestinations =
        topLevelDestinations.map { destination ->
            CabNavigationDestination(
                route = destination.route,
                label = stringResource(destination.labelRes),
                icon = destination.icon,
                testTag = destination.testTag,
            )
        }
    val motion = CabTheme.motion

    CabNavigationSuite(
        destinations = navigationDestinations,
        selectedRoute = currentRoute,
        onNavigate = { destination -> navController.navigateToTopLevel(destination.route) },
        modifier = modifier,
        visible = isTopLevelDestination,
    ) {
        CabPredictiveBackContainer(
            enabled = navController.previousBackStackEntry != null,
            onBack = { navController.popBackStack() },
        ) {
            NavHost(
                navController = navController,
                startDestination = DRIVE_ROUTE,
                enterTransition = { fadeIn(motion.fadeThrough) },
                exitTransition = { fadeOut(motion.duration(durationMillis = 90)) },
                popEnterTransition = { fadeIn(motion.fadeThrough) },
                popExitTransition = { fadeOut(motion.duration(durationMillis = 90)) },
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
