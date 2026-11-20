package dev.opencab.core.designsystem

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.opencab.core.designsystem.component.CabConnectionStatus
import dev.opencab.core.designsystem.component.CabDirection
import dev.opencab.core.designsystem.component.ConfirmSheetContent
import dev.opencab.core.designsystem.component.FunctionKey
import dev.opencab.core.designsystem.component.FunctionKeyState
import dev.opencab.core.designsystem.component.PrimaryActionButton
import dev.opencab.core.designsystem.component.SpeedDisplay
import dev.opencab.core.designsystem.component.StatusPill
import dev.opencab.core.designsystem.theme.CabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statusPillExposesOneAccessibleNode() {
        composeRule.setContent {
            CabTheme {
                StatusPill(status = CabConnectionStatus.Reconnecting, attempt = 3)
            }
        }

        composeRule.onNodeWithContentDescription("Reconnecting, attempt 3").assertExists()
    }

    @Test
    fun activeFunctionKeyIsSelectedAndNamed() {
        composeRule.setContent {
            CabTheme {
                FunctionKey(
                    label = "Whistle",
                    state = FunctionKeyState.Active,
                    onClick = {},
                    contentDescription = "Function 3, Whistle",
                )
            }
        }

        composeRule.onNodeWithContentDescription("Function 3, Whistle").assertIsSelected()
    }

    @Test
    fun speedDisplayExposesProgressAndDirection() {
        composeRule.setContent {
            CabTheme {
                SpeedDisplay(percent = 84, direction = CabDirection.Forward)
            }
        }

        composeRule.onNode(hasStateDescription("84 percent, forward")).assertExists()
    }

    @Test
    fun speedDisplayExplainsUnknownSpeed() {
        composeRule.setContent {
            CabTheme {
                SpeedDisplay(percent = null)
            }
        }

        composeRule.onNodeWithContentDescription("Speed unknown").assertExists()
    }

    @Test
    fun primaryActionButtonIsDisabledWhenUnavailable() {
        composeRule.setContent {
            CabTheme {
                PrimaryActionButton(text = "Stop train", onClick = {}, enabled = false)
            }
        }

        composeRule.onNodeWithText("Stop train").assertIsNotEnabled()
    }

    @Test
    fun confirmSheetContentHasACallableAction() {
        composeRule.setContent {
            CabTheme {
                ConfirmSheetContent(
                    title = "Release 3412?",
                    message = "Stops the train, then frees the address.",
                    confirmLabel = "Release",
                    onConfirm = {},
                    onDismiss = {},
                    destructive = true,
                )
            }
        }

        composeRule.onNodeWithText("Release").assertHasClickAction()
    }
}
