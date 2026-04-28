package com.aldrich.safeai.overlay

enum class BlockedOverlayRecoveryAction(
    val stopsShield: Boolean,
) {
    OPEN_MAIN_MENU(stopsShield = false),
    OPEN_HOME_TO_CLEAR_RECENTS(stopsShield = false);

    companion object {
        fun defaultActions(): List<BlockedOverlayRecoveryAction> {
            return listOf(OPEN_MAIN_MENU, OPEN_HOME_TO_CLEAR_RECENTS)
        }
    }
}
