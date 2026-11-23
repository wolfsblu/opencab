package dev.opencab.feature.throttle

import dev.opencab.core.data.throttle.SpeedSteps
import dev.opencab.core.data.throttle.ThrottleAcquisitionState
import dev.opencab.core.data.throttle.ThrottleFunctionUiState
import dev.opencab.core.data.throttle.ThrottleHealth
import dev.opencab.core.data.throttle.ThrottleIssue
import dev.opencab.core.data.throttle.ThrottleLinkState
import dev.opencab.core.data.throttle.ThrottleSlotUiState
import dev.opencab.core.data.throttle.ThrottleUiState
import dev.opencab.core.model.ConnectionState
import dev.opencab.core.model.ConnectionTarget
import dev.opencab.core.model.ServerEndpoint
import dev.opencab.core.protocol.AddressType
import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.TrackPowerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveStateMappingTest {
    private val address = LocoAddress(AddressType.LONG, 3412)
    private val target = ConnectionTarget("Junction Yard", ServerEndpoint("192.168.1.24"))

    @Test
    fun mapsConnectedSlotWithStepsAndFunctions() {
        val state = mapDriveUiState(
            throttle = throttle(slot = slot()),
            connection = ConnectionState.Connected(target),
        )

        assertEquals(DriveLink.Connected, state.link)
        assertEquals("Junction Yard", state.layoutName)
        assertEquals("Black Five", state.loco?.name)
        assertEquals("3412", state.loco?.address)
        assertEquals(128, state.loco?.speedStepCount)
        assertEquals(64, state.loco?.speedStep)
        assertEquals(DriveDirection.Forward, state.loco?.direction)
        assertEquals("Whistle", state.loco?.functions?.first { it.number == 1 }?.label)
        assertTrue(state.loco?.canControl == true)
        assertTrue(state.controlsEnabled)
    }

    @Test
    fun localFunctionLabelsOverrideTheLayoutLabels() {
        val state = mapDriveUiState(
            throttle = throttle(slot = slot()),
            connection = ConnectionState.Connected(target),
            localFunctionLabels = mapOf(1 to "Horn"),
        )

        assertEquals("Horn", state.loco?.functions?.first { it.number == 1 }?.label)
        assertEquals("Headlight", state.loco?.functions?.first { it.number == 0 }?.label)
    }

    @Test
    fun reportsInUseIssueAndDisablesControl() {
        val state = mapDriveUiState(
            throttle = throttle(
                slot = slot(
                    acquisition = ThrottleAcquisitionState.IN_USE,
                    health = ThrottleHealth.ONLINE,
                    issue = ThrottleIssue.InUse(address),
                ),
            ),
            connection = ConnectionState.Connected(target),
        )

        assertEquals(DriveIssue.InUse, state.loco?.issue)
        assertEquals(DriveAcquisition.InUse, state.loco?.acquisition)
        assertFalse(state.controlsEnabled)
    }

    @Test
    fun keepsLastKnownValueWhileReconnecting() {
        val state = mapDriveUiState(
            throttle = throttle(
                link = ThrottleLinkState.RECONNECTING,
                reconnectAttempt = 4,
                slot = slot(
                    health = ThrottleHealth.STALE,
                    acquisition = ThrottleAcquisitionState.ACQUIRED,
                ),
            ),
            connection = ConnectionState.Reconnecting(target, attempt = 4),
        )

        assertEquals(DriveLink.Reconnecting, state.link)
        assertEquals(4, state.reconnectAttempt)
        assertEquals(DriveHealth.Stale, state.loco?.health)
        assertEquals(64, state.loco?.speedStep)
        assertFalse(state.controlsEnabled)
    }

    @Test
    fun fallsBackToConnectionStateBeforeTheEngineLinks() {
        val state = mapDriveUiState(
            throttle = ThrottleUiState(link = ThrottleLinkState.UNLINKED),
            connection = ConnectionState.Connected(target),
        )

        assertEquals(DriveLink.Connected, state.link)
        assertNull(state.loco)
    }

    @Test
    fun mapsTrackPowerOff() {
        val state = mapDriveUiState(
            throttle = throttle(slot = slot()).copy(trackPower = TrackPowerState.OFF),
            connection = ConnectionState.Connected(target),
        )

        assertFalse(state.trackPowerOn)
    }

    @Test
    fun scaleEstimateRoundsAndClamps() {
        val scale = DriveScale(maxScaleSpeed = 120)

        assertEquals(0, scale.estimatedSpeed(0f))
        assertEquals(60, scale.estimatedSpeed(0.5f))
        assertEquals(120, scale.estimatedSpeed(2f))
    }

    @Test
    fun hapticIntensityGatesTheVocabulary() {
        assertTrue(DriveHaptics(intensity = 1f).allows(DriveHapticToken.Confirm))
        assertFalse(DriveHaptics(enabled = false).allows(DriveHapticToken.Detent))
        assertFalse(DriveHaptics(intensity = 0.2f).allows(DriveHapticToken.Limit))
        assertTrue(DriveHaptics(intensity = 0.2f).allows(DriveHapticToken.Detent))
    }

    private fun throttle(
        link: ThrottleLinkState = ThrottleLinkState.CONNECTED,
        reconnectAttempt: Int? = null,
        slot: ThrottleSlotUiState? = null,
    ): ThrottleUiState = ThrottleUiState(
        link = link,
        reconnectAttempt = reconnectAttempt,
        trackPower = TrackPowerState.ON,
        slots = listOfNotNull(slot),
    )

    private fun slot(
        health: ThrottleHealth = ThrottleHealth.ONLINE,
        acquisition: ThrottleAcquisitionState = ThrottleAcquisitionState.ACQUIRED,
        issue: ThrottleIssue? = null,
    ): ThrottleSlotUiState = ThrottleSlotUiState(
        address = address,
        name = "Black Five",
        speedSteps = SpeedSteps.ONE_HUNDRED_TWENTY_EIGHT,
        speed = 0.5,
        direction = Direction.FORWARD,
        functions = listOf(
            ThrottleFunctionUiState(
                number = 0,
                label = "Headlight",
                isOn = true,
                isMomentary = false,
                isEnabled = health == ThrottleHealth.ONLINE,
            ),
            ThrottleFunctionUiState(
                number = 1,
                label = "Whistle",
                isOn = false,
                isMomentary = true,
                isEnabled = health == ThrottleHealth.ONLINE,
            ),
        ),
        acquisition = acquisition,
        health = health,
        desired = true,
        emergencyStop = false,
        issue = issue,
    )
}
