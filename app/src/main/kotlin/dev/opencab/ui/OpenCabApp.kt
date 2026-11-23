package dev.opencab.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.opencab.core.designsystem.navigation.CabNavigationDestination
import dev.opencab.core.designsystem.navigation.CabNavigationSuite
import dev.opencab.core.designsystem.navigation.CabPredictiveBackContainer
import dev.opencab.core.designsystem.theme.CabMotion
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.feature.connection.CONNECTION_ROUTE
import dev.opencab.feature.connection.ConnectionRoute
import dev.opencab.feature.roster.LocoEditorRoute
import dev.opencab.feature.roster.LocoPickerSheet
import dev.opencab.feature.roster.ROSTER_CREATE_ROUTE
import dev.opencab.feature.roster.ROSTER_EDIT_ROUTE
import dev.opencab.feature.roster.ROSTER_ROUTE
import dev.opencab.feature.roster.RosterRoute
import dev.opencab.feature.roster.rosterEditRoute
import dev.opencab.feature.settings.SETTINGS_ROUTE
import dev.opencab.feature.settings.SettingsRoute
import dev.opencab.feature.throttle.DRIVE_ROUTE
import dev.opencab.feature.throttle.DriveRoute
import dev.opencab.ui.navigation.topLevelDestinations

@Composable
fun OpenCabApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    onExit: () -> Unit = {},
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelDestination =
        currentRoute != null && topLevelDestinations.any { destination -> destination.route == currentRoute }
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
    var pickerVisible by rememberSaveable { mutableStateOf(false) }

    CabNavigationSuite(
        destinations = navigationDestinations,
        selectedRoute = currentRoute,
        onNavigate = { destination -> navController.navigateToTopLevel(destination.route) },
        modifier = modifier,
        visible = isTopLevelDestination,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CabPredictiveBackContainer(
                enabled = navController.previousBackStackEntry != null,
                onBack = { navController.popBackStack() },
            ) {
                OpenCabNavHost(
                    navController = navController,
                    motion = motion,
                    onChooseLocomotive = { pickerVisible = true },
                    onExit = onExit,
                )
            }
            if (pickerVisible) {
                LocoPickerSheet(onDismissRequest = { pickerVisible = false })
            }
        }
    }
}

@Composable
private fun OpenCabNavHost(
    navController: NavHostController,
    motion: CabMotion,
    onChooseLocomotive: () -> Unit,
    onExit: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = CONNECTION_ROUTE,
        enterTransition = { fadeIn(motion.fadeThrough) },
        exitTransition = { fadeOut(motion.duration(durationMillis = 90)) },
        popEnterTransition = { fadeIn(motion.fadeThrough) },
        popExitTransition = { fadeOut(motion.duration(durationMillis = 90)) },
    ) {
        composable(DRIVE_ROUTE) {
            DriveRoute(
                onOpenConnection = { navController.navigate(CONNECTION_ROUTE) },
                onChooseLocomotive = onChooseLocomotive,
            )
        }
        composable(ROSTER_ROUTE) {
            RosterRoute(
                onAddLoco = { navController.navigate(ROSTER_CREATE_ROUTE) },
                onEditLoco = { locoId -> navController.navigate(rosterEditRoute(locoId)) },
                onOpenDrive = { navController.navigateToTopLevel(DRIVE_ROUTE) },
            )
        }
        composable(ROSTER_CREATE_ROUTE) {
            LocoEditorRoute(
                onBack = { navController.popBackStack() },
                onSaved = { _, _ -> navController.popBackStack() },
            )
        }
        composable(ROSTER_EDIT_ROUTE) {
            LocoEditorRoute(
                onBack = { navController.popBackStack() },
                onSaved = { _, _ -> navController.popBackStack() },
            )
        }
        composable(SETTINGS_ROUTE) {
            SettingsRoute(
                onOpenConnection = { navController.navigate(CONNECTION_ROUTE) },
            )
        }
        composable(CONNECTION_ROUTE) {
            ConnectionRoute(
                onBack = {
                    if (!navController.popBackStack()) onExit()
                },
                onOpenDrive = { navController.openDriveFromConnection() },
            )
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        // Drive is the home destination; Connect is a gate that is removed once linked.
        popUpTo(DRIVE_ROUTE) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Makes Drive the visible destination after a successful connect.
 *
 * If Drive is already on the stack (Connection was opened from Drive or Settings), pop back to
 * it. On a cold start Connection is the graph root, so Drive is pushed and Connection is removed
 * from the back stack: Connect is a gate, not a screen to return to.
 */
private fun NavHostController.openDriveFromConnection() {
    if (popBackStack(DRIVE_ROUTE, inclusive = false)) return
    navigate(DRIVE_ROUTE) {
        popUpTo(CONNECTION_ROUTE) { inclusive = true }
        launchSingleTop = true
    }
}
