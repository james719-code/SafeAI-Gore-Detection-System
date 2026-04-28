package com.aldrich.safeai.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BlockedOverlayRecoveryActionTest {

    @Test
    fun defaultActionsLetUserReturnToSafeAiAndGoHomeForRecentsCleanup() {
        assertEquals(
            listOf(
                BlockedOverlayRecoveryAction.OPEN_MAIN_MENU,
                BlockedOverlayRecoveryAction.OPEN_HOME_TO_CLEAR_RECENTS,
            ),
            BlockedOverlayRecoveryAction.defaultActions(),
        )
    }

    @Test
    fun recoveryActionsKeepShieldRunning() {
        for (action in BlockedOverlayRecoveryAction.defaultActions()) {
            assertFalse(action.stopsShield)
        }
    }
}
