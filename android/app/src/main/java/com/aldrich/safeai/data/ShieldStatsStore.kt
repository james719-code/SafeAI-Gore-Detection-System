package com.aldrich.safeai.data

import android.content.Context
import androidx.core.content.edit

data class ShieldStats(
    val blockedImageCount: Int,
    val lastBlockedAtEpochMs: Long?,
)

object ShieldStatsStore {
    private const val PREFERENCES_NAME = "safeai_preferences"
    private const val KEY_BLOCKED_IMAGE_COUNT = "blocked_image_count"
    private const val KEY_LAST_BLOCKED_AT_EPOCH_MS = "last_blocked_at_epoch_ms"

    fun read(context: Context): ShieldStats {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return ShieldStats(
            blockedImageCount = prefs.getInt(KEY_BLOCKED_IMAGE_COUNT, 0).coerceAtLeast(0),
            lastBlockedAtEpochMs = prefs.getLong(KEY_LAST_BLOCKED_AT_EPOCH_MS, 0L)
                .takeIf { it > 0L },
        )
    }

    fun recordBlockedTransition(
        context: Context,
        blockedAtEpochMs: Long = System.currentTimeMillis(),
    ): ShieldStats {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val nextCount = prefs.getInt(KEY_BLOCKED_IMAGE_COUNT, 0).coerceAtLeast(0) + 1
        val safeTimestamp = blockedAtEpochMs.coerceAtLeast(1L)

        prefs.edit {
            putInt(KEY_BLOCKED_IMAGE_COUNT, nextCount)
            putLong(KEY_LAST_BLOCKED_AT_EPOCH_MS, safeTimestamp)
        }

        return ShieldStats(
            blockedImageCount = nextCount,
            lastBlockedAtEpochMs = safeTimestamp,
        )
    }
}
