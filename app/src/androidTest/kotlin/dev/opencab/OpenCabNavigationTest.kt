package dev.opencab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.opencab.ui.OpenCabTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenCabNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun driveIsTheStartDestination() {
        composeRule.onNodeWithTag("screen_drive").assertIsDisplayed()
    }

    @Test
    fun navigationDestinationsAreVisible() {
        composeRule.onNodeWithTag(OpenCabTestTags.NAV_DRIVE).assertIsDisplayed()
        composeRule.onNodeWithTag(OpenCabTestTags.NAV_ROSTER).assertIsDisplayed()
        composeRule.onNodeWithTag(OpenCabTestTags.NAV_SETTINGS).assertIsDisplayed()
    }

    @Test
    fun canNavigateToRoster() {
        composeRule.onNodeWithTag(OpenCabTestTags.NAV_ROSTER).performClick()

        composeRule.onNodeWithTag("screen_roster").assertIsDisplayed()
    }

    @Test
    fun canOpenConnectionRoute() {
        composeRule.onNodeWithTag("drive_open_connection").performClick()

        composeRule.onNodeWithTag("screen_connection").assertIsDisplayed()
    }
}
