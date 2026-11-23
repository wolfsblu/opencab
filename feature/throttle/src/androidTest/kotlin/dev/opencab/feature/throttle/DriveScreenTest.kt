package dev.opencab.feature.throttle

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.opencab.core.designsystem.theme.CabTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DriveScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = RecordingActions()

    private fun setContent(uiState: DriveUiState) {
        composeRule.setContent {
            CabTheme {
                DriveScreen(uiState = uiState, actions = actions)
            }
        }
    }

    private fun openFunctionsTab() {
        if (composeRule.onAllNodesWithTag(DriveTestTags.FUNCTIONS_TAB).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(DriveTestTags.FUNCTIONS_TAB).performClick()
        }
    }

    @Test
    fun connectedScreenShowsStepCountAndStop() {
        setContent(DrivePreviewData.connected())

        composeRule.onNodeWithTag(DriveTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(DriveTestTags.STEP).assertTextEquals("42")
        composeRule.onNodeWithTag(DriveTestTags.STOP).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun draggingTheBackgroundDoesNotMoveTheThrottle() {
        setContent(DrivePreviewData.connected())

        composeRule.onNodeWithTag(DriveTestTags.LEVER).performTouchInput {
            down(Offset(center.x, 20f))
            moveTo(Offset(center.x, center.y))
            up()
        }

        assertTrue(actions.speeds.isEmpty())
    }

    @Test
    fun draggingTheHandleSendsAThrottleValue() {
        setContent(DrivePreviewData.connected())

        val handle = composeRule.onNodeWithTag(DriveTestTags.HANDLE).getUnclippedBoundsInRoot()
        val lever = composeRule.onNodeWithTag(DriveTestTags.LEVER).getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag(DriveTestTags.HANDLE).performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f))
            up()
        }

        assertTrue(
            "handle=$handle lever=$lever speeds=${actions.speeds}",
            actions.speeds.isNotEmpty(),
        )
    }

    @Test
    fun leverExposesAdjustableSemantics() {
        setContent(DrivePreviewData.connected())

        composeRule.onNodeWithTag(DriveTestTags.LEVER)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(1f)
            }

        assertEquals(1f, actions.speeds.last(), 0.001f)
    }

    @Test
    fun stopActionIsImmediate() {
        setContent(DrivePreviewData.connected())

        composeRule.onNodeWithTag(DriveTestTags.STOP).performClick()

        assertEquals(1, actions.stopCount)
    }

    @Test
    fun reconnectingBannerDisablesMovementControls() {
        setContent(DrivePreviewData.reconnecting())

        composeRule.onNodeWithTag(DriveTestTags.BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Reconnecting… (attempt 3)", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(DriveTestTags.DIRECTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(DriveTestTags.STOP).assertIsDisplayed()
    }

    @Test
    fun inUseStateOffersTakeControl() {
        setContent(DrivePreviewData.inUse())

        composeRule.onNodeWithTag(DriveTestTags.ISSUE).assertIsDisplayed()
        composeRule.onNodeWithTag(DriveTestTags.STOP).assertIsNotEnabled()
        composeRule.onNodeWithText("Take control").performClick()

        assertEquals(1, actions.takeControlCount)
    }

    @Test
    fun directionWhileMovingStopsBeforeReversing() {
        setContent(DrivePreviewData.connected())

        composeRule.onNodeWithTag(DriveTestTags.DIRECTION).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Stop, then reverse?"), timeoutMillis = 5_000)
        assertTrue(actions.directions.isEmpty())

        composeRule.onNodeWithText("Stop & reverse").performClick()

        assertEquals(1, actions.stopCount)
        assertEquals(listOf(DriveDirection.Reverse), actions.directions)
    }

    @Test
    fun releaseWhileMovingRequiresConfirmation() {
        setContent(DrivePreviewData.connected())

        composeRule.onNodeWithTag(DriveTestTags.MENU).performClick()
        composeRule.onNodeWithTag(DriveTestTags.RELEASE).performClick()

        composeRule.waitUntilAtLeastOneExists(hasText("Release 3412?"), timeoutMillis = 5_000)
        assertEquals(0, actions.releaseCount)

        composeRule.onNodeWithText("Release").performClick()

        assertEquals(1, actions.releaseCount)
    }

    @Test
    fun maximumSpeedIsMarked() {
        setContent(DrivePreviewData.maxSpeed())

        composeRule.onNodeWithTag(DriveTestTags.STEP).assertTextEquals("128")
        composeRule.onNodeWithText("MAX").assertIsDisplayed()
    }

    @Test
    fun activeAndMomentaryFunctionsAreExposed() {
        setContent(DrivePreviewData.activeFunctions())
        openFunctionsTab()

        composeRule.onNodeWithContentDescription("Function 0, Headlight").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Function 2, Bell").assertIsDisplayed()
    }

    @Test
    fun functionsTabKeepsTheControlDeckPinnedAndHidesTheLever() {
        setContent(DrivePreviewData.connected())

        composeRule.onNodeWithTag(DriveTestTags.FUNCTIONS_TAB).performClick()

        composeRule.onNodeWithTag(DriveTestTags.FUNCTIONS).assertIsDisplayed()
        composeRule.onNodeWithTag(DriveTestTags.STOP).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag(DriveTestTags.DIRECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DriveTestTags.LEVER).assertDoesNotExist()

        composeRule.onNodeWithTag(DriveTestTags.THROTTLE_TAB).performClick()

        composeRule.onNodeWithTag(DriveTestTags.LEVER).assertIsDisplayed()
        composeRule.onNodeWithTag(DriveTestTags.CONTROL_DECK).assertIsDisplayed()
    }

    @Test
    fun momentaryFunctionUsesPressAndHold() {
        val momentary = DrivePreviewData.activeFunctions().let { state ->
            state.copy(
                loco = state.loco?.copy(
                    functions = state.loco.functions.map { function ->
                        if (function.number == 2) function.copy(isMomentary = true) else function
                    },
                ),
            )
        }
        setContent(momentary)
        openFunctionsTab()

        composeRule.onNodeWithTag(DriveTestTags.function(2)).performTouchInput {
            down(center)
            up()
        }

        assertEquals(listOf(2), actions.holdStarts)
        assertEquals(listOf(2), actions.holdEnds)
    }

    @Test
    fun longLabelsDoNotHideTheLocoAddress() {
        setContent(DrivePreviewData.longLabels())

        composeRule.onNodeWithText("45305 · The North", substring = true).assertIsDisplayed()
        composeRule
            .onNodeWithText("LMS Stanier Class 5 4-6-0 Black Five 45305", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun controlDeckStaysInsideTheThrottleAssembly() {
        setContent(DrivePreviewData.connected())

        val panel = composeRule.onNodeWithTag(DriveTestTags.LEVER_PANEL).getUnclippedBoundsInRoot()
        val deck = composeRule.onNodeWithTag(DriveTestTags.CONTROL_DECK).getUnclippedBoundsInRoot()
        val direction = composeRule.onNodeWithTag(DriveTestTags.DIRECTION).getUnclippedBoundsInRoot()
        val stop = composeRule.onNodeWithTag(DriveTestTags.STOP).getUnclippedBoundsInRoot()

        assertTrue(deck.left >= panel.left && deck.right <= panel.right)
        assertTrue(direction.left >= deck.left && stop.right <= deck.right)
        assertTrue(stop.left > direction.right)
        assertTrue(direction.bottom - direction.top >= 64.dp)
        assertTrue(stop.bottom - stop.top >= 64.dp)
    }

    @Test
    fun landscapeAssemblyStacksControlsUnderReadoutAndSpansLeverHeight() {
        composeRule.setContent {
            CabTheme {
                Box(Modifier.requiredSize(width = 900.dp, height = 420.dp)) {
                    DriveScreen(uiState = DrivePreviewData.connected(), actions = actions)
                }
            }
        }

        val panel = composeRule.onNodeWithTag(DriveTestTags.LEVER_PANEL).getUnclippedBoundsInRoot()
        val readout = composeRule.onNodeWithTag(DriveTestTags.READOUT).getUnclippedBoundsInRoot()
        val deck = composeRule.onNodeWithTag(DriveTestTags.CONTROL_DECK).getUnclippedBoundsInRoot()
        val lever = composeRule.onNodeWithTag(DriveTestTags.LEVER).getUnclippedBoundsInRoot()

        assertTrue(deck.top >= readout.bottom)
        assertTrue(lever.left >= deck.right)
        assertTrue(
            "leverHeight=${lever.bottom - lever.top} panelHeight=${panel.bottom - panel.top}",
            lever.bottom - lever.top >= (panel.bottom - panel.top) - 32.dp,
        )
    }

    private class RecordingActions : DriveActions {
        val speeds = mutableListOf<Float>()
        val holdStarts = mutableListOf<Int>()
        val holdEnds = mutableListOf<Int>()
        val directions = mutableListOf<DriveDirection>()
        var stopCount: Int = 0
        var releaseCount: Int = 0
        var takeControlCount: Int = 0
        var toggledFunctions = mutableListOf<Int>()

        override fun onSpeedChanged(speed: Float) {
            speeds += speed
        }

        override fun onStop() {
            stopCount += 1
        }

        override fun onDirectionChange(direction: DriveDirection) {
            directions += direction
        }

        override fun onToggleFunction(number: Int) {
            toggledFunctions += number
        }

        override fun onFunctionHoldStart(number: Int) {
            holdStarts += number
        }

        override fun onFunctionHoldEnd(number: Int) {
            holdEnds += number
        }

        override fun onTakeControl() {
            takeControlCount += 1
        }

        override fun onRelease() {
            releaseCount += 1
        }
    }
}
