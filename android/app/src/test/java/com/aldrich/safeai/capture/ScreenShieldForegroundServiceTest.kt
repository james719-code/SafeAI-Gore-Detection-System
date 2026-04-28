package com.aldrich.safeai.capture

import com.aldrich.safeai.inference.SafetyLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenShieldForegroundServiceTest {

    @Test
    fun safeFrameDoesNotClearActiveBlockedOverlay() {
        assertFalse(ScreenShieldForegroundService.shouldHideBlockedOverlay(SafetyLevel.SAFE))
    }

    @Test
    fun overlayRecoveryAllowsFutureBlockedOverlay() {
        assertTrue(ScreenShieldForegroundService.shouldResetBlockedOverlayStateAfterRecovery())
    }

    @Test
    fun recoveryWindowSuppressesImmediateBlockedOverlay() {
        assertFalse(
            ScreenShieldForegroundService.shouldShowBlockedOverlay(
                hasShownBlockedOverlay = false,
                nowMs = 1_000L,
                suppressBlockedOverlayUntilMs = 5_000L,
            )
        )
    }

    @Test
    fun blockedOverlayCanShowAfterRecoveryWindow() {
        assertTrue(
            ScreenShieldForegroundService.shouldShowBlockedOverlay(
                hasShownBlockedOverlay = false,
                nowMs = 6_000L,
                suppressBlockedOverlayUntilMs = 5_000L,
            )
        )
    }
}
