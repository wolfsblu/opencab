package dev.opencab

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.opencab.feature.connection.ConnectionTestTags
import dev.opencab.ui.OpenCabTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OpenCabNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForInitialContent() {
        composeRule.waitUntilAtLeastOneExists(
            matcher = hasTestTag(ConnectionTestTags.SCREEN),
            timeoutMillis = 10_000,
        )
    }

    @Test
    fun connectionIsTheStartDestination() {
        composeRule.onNodeWithTag(ConnectionTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun navigationIsHiddenOnTheConnectionGate() {
        composeRule.onNodeWithTag(OpenCabTestTags.NAV_DRIVE).assertDoesNotExist()
        composeRule.onNodeWithTag(OpenCabTestTags.NAV_ROSTER).assertDoesNotExist()
        composeRule.onNodeWithTag(OpenCabTestTags.NAV_SETTINGS).assertDoesNotExist()
    }

    @Test
    fun demoModeIsTheObviousRouteIntoDrive() {
        composeRule.onNodeWithTag(ConnectionTestTags.START_DEMO)
            .performScrollTo()
            .performClick()

        composeRule.waitUntilAtLeastOneExists(
            matcher = hasTestTag("screen_drive"),
            timeoutMillis = 10_000,
        )

        composeRule.onNodeWithTag("screen_drive").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_demo_banner").assertIsDisplayed()
        composeRule.onNodeWithTag(OpenCabTestTags.NAV_DRIVE).assertIsDisplayed()
    }
}
