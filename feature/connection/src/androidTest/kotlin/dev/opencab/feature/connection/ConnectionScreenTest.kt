package dev.opencab.feature.connection

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ConnectionTarget
import dev.opencab.core.model.ServerEndpoint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val endpoint = ServerEndpoint(host = "192.168.1.24", port = 12090)
    private val target = ConnectionTarget(name = "Junction Yard", endpoint = endpoint)

    private fun setContent(state: ConnectionUiState) {
        composeRule.setContent {
            CabTheme {
                ConnectionScreen(
                    uiState = state,
                    onAction = {},
                    onManualNameChange = {},
                    onManualHostChange = {},
                    onManualPortChange = {},
                )
            }
        }
    }

    @Test
    fun scanningStateIsExplicit() {
        setContent(
            ConnectionUiState(
                permission = PermissionUiState.Granted,
                scanPhase = ScanPhase.Scanning,
            ),
        )

        composeRule.onNodeWithTag(ConnectionTestTags.SCANNING).assertIsDisplayed()
        composeRule.onNodeWithText("Searching this Wi-Fi for WiThrottle servers.").assertIsDisplayed()
    }

    @Test
    fun emptyStateKeepsManualEntryAvailable() {
        setContent(
            ConnectionUiState(
                permission = PermissionUiState.Granted,
                scanPhase = ScanPhase.Complete,
            ),
        )

        composeRule.onNodeWithText("No layouts found nearby").assertIsDisplayed()
        composeRule.onNodeWithTag(ConnectionTestTags.MANUAL_ENTRY)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun deniedPermissionOffersASettingsLink() {
        setContent(
            ConnectionUiState(
                permission = PermissionUiState.Denied,
                scanPhase = ScanPhase.Complete,
            ),
        )

        composeRule.onNodeWithTag(ConnectionTestTags.PERMISSION_DENIED).assertIsDisplayed()
        composeRule.onNodeWithTag(ConnectionTestTags.PERMISSION_SETTINGS).assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").assertIsDisplayed()
    }

    @Test
    fun connectingStateOffersCancel() {
        setContent(
            ConnectionUiState(
                permission = PermissionUiState.Granted,
                scanPhase = ScanPhase.Complete,
                link = LinkUiState(target = target, phase = LinkPhase.Connecting),
            ),
        )

        composeRule.onNodeWithTag(ConnectionTestTags.CONNECTING).assertIsDisplayed()
        composeRule.onNodeWithText("Connecting to Junction Yard", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ConnectionTestTags.CANCEL).assertIsDisplayed()
    }

    @Test
    fun failedStateUsesPlainLanguageAndASRecoveryAction() {
        setContent(
            ConnectionUiState(
                permission = PermissionUiState.Granted,
                scanPhase = ScanPhase.Complete,
                failure = FailureUiState(target = target, error = ConnectionError.TimedOut),
            ),
        )

        composeRule.onNodeWithTag(ConnectionTestTags.FAILED).assertIsDisplayed()
        composeRule.onNodeWithText("Nothing answered").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun connectedStateMakesTheDriveTransitionObvious() {
        setContent(
            ConnectionUiState(
                permission = PermissionUiState.Granted,
                scanPhase = ScanPhase.Complete,
                link = LinkUiState(target = target, phase = LinkPhase.Connected),
            ),
        )

        composeRule.onNodeWithTag(ConnectionTestTags.CONNECTED).assertIsDisplayed()
        composeRule.onNodeWithTag(ConnectionTestTags.OPEN_DRIVE).assertIsDisplayed()
        composeRule.onNodeWithText("Connected to Junction Yard").assertIsDisplayed()
    }

    @Test
    fun demoEntryIsAvailableAndClearlyLabelled() {
        setContent(
            ConnectionUiState(
                permission = PermissionUiState.Needed,
                scanPhase = ScanPhase.Complete,
            ),
        )

        composeRule.onNodeWithTag(ConnectionTestTags.MANUAL_ENTRY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ConnectionTestTags.START_DEMO).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Demo mode").assertIsDisplayed()
    }
}
