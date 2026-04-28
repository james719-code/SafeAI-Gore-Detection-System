package com.aldrich.safeai.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aldrich.safeai.R
import com.aldrich.safeai.ui.components.CompactCardGrid
import com.aldrich.safeai.ui.components.CompactInfoCard
import com.aldrich.safeai.ui.components.ControlHeroCard
import com.aldrich.safeai.ui.components.ProgressSummary
import com.aldrich.safeai.ui.components.ScreenColumn
import com.aldrich.safeai.ui.components.SecondaryActionButton
import com.aldrich.safeai.ui.components.SectionCard
import com.aldrich.safeai.ui.theme.ThemeMode

@Composable
fun SetupScreen(
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    modelReady: Boolean,
    modelStatusText: String,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onRunModelSelfTest: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notificationRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notificationGranted = hasNotificationPermission || !notificationRequired
    val allReady = hasOverlayPermission && notificationGranted && modelReady
    val completedSteps = listOf(hasOverlayPermission, notificationGranted, modelReady).count { it }

    ScreenColumn(modifier = modifier.fillMaxSize()) {
        ControlHeroCard(
            title = stringResource(R.string.setup_title_short),
            subtitle = if (allReady) stringResource(R.string.setup_ready_body_short) else stringResource(R.string.setup_subtitle_short),
            badgeLabel = stringResource(R.string.setup_progress_badge, completedSteps, 3),
            icon = if (allReady) Icons.Rounded.CheckCircle else Icons.Rounded.Settings,
        ) {
            ProgressSummary(
                completedSteps = completedSteps,
                totalSteps = 3,
                label = stringResource(R.string.setup_progress_label),
            )
        }

        CompactCardGrid(
            first = {
                CompactInfoCard(
                    label = stringResource(R.string.setup_card_overlay),
                    value = readyLabel(hasOverlayPermission),
                    icon = Icons.Rounded.Visibility,
                    isGood = hasOverlayPermission,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            second = {
                CompactInfoCard(
                    label = stringResource(R.string.setup_card_notification),
                    value = readyLabel(notificationGranted),
                    icon = Icons.Rounded.Notifications,
                    isGood = notificationGranted,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            third = {
                CompactInfoCard(
                    label = stringResource(R.string.setup_card_model),
                    value = readyLabel(modelReady),
                    icon = Icons.Rounded.SmartToy,
                    isGood = modelReady,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            fourth = {
                CompactInfoCard(
                    label = stringResource(R.string.setup_card_capture),
                    value = stringResource(R.string.setup_capture_on_start),
                    icon = Icons.Rounded.Visibility,
                    isGood = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )

        SectionCard(
            title = stringResource(R.string.setup_actions_title),
            subtitle = modelStatusText,
            icon = Icons.Rounded.Settings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryActionButton(
                    label = stringResource(R.string.setup_overlay_action_short),
                    icon = Icons.Rounded.Visibility,
                    onClick = onOpenOverlaySettings,
                )
                SecondaryActionButton(
                    label = stringResource(R.string.setup_notification_action_short),
                    icon = Icons.Rounded.Notifications,
                    onClick = onRequestNotificationPermission,
                    enabled = notificationRequired,
                )
                SecondaryActionButton(
                    label = stringResource(R.string.model_self_test_button_short),
                    icon = Icons.Rounded.Refresh,
                    onClick = onRunModelSelfTest,
                )
            }
        }

        SectionCard(
            title = stringResource(R.string.setup_appearance_title_short),
            subtitle = stringResource(R.string.setup_appearance_body),
            icon = Icons.Rounded.Palette,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val selected = mode == themeMode
                    FilterChip(
                        selected = selected,
                        onClick = { onThemeModeChange(mode) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(text = stringResource(mode.labelRes))
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.26f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun readyLabel(isReady: Boolean): String {
    return if (isReady) {
        stringResource(R.string.setup_permission_granted)
    } else {
        stringResource(R.string.setup_permission_required)
    }
}
