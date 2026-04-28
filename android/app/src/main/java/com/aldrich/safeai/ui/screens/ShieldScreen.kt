package com.aldrich.safeai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aldrich.safeai.R
import com.aldrich.safeai.ui.components.CompactCardGrid
import com.aldrich.safeai.ui.components.CompactInfoCard
import com.aldrich.safeai.ui.components.ControlHeroCard
import com.aldrich.safeai.ui.components.PrimaryActionButton
import com.aldrich.safeai.ui.components.ScreenColumn
import com.aldrich.safeai.ui.components.SecondaryActionButton
import com.aldrich.safeai.ui.components.SectionCard
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun ShieldScreen(
    isShieldRunning: Boolean,
    threshold: Float,
    thresholdRange: ClosedFloatingPointRange<Float>,
    blockedImageCount: Int,
    lastBlockedAtEpochMs: Long?,
    modelReady: Boolean,
    modelStatusText: String,
    onRunModelSelfTest: () -> Unit,
    onThresholdChange: (Float) -> Unit,
    onApplyThreshold: () -> Unit,
    onStartShield: () -> Unit,
    onStopShield: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thresholdPercent = (threshold * 100f).roundToInt()
    val timestampFormatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }
    val lastBlockedLabel = lastBlockedAtEpochMs
        ?.let { timestampFormatter.format(Date(it)) }
        ?: stringResource(R.string.shield_last_blocked_none_short)

    ScreenColumn(modifier = modifier.fillMaxSize()) {
        ControlHeroCard(
            title = stringResource(R.string.shield_title_short),
            subtitle = if (modelReady) {
                if (isShieldRunning) stringResource(R.string.shield_live_status_body_short) else stringResource(R.string.shield_empty_body_short)
            } else {
                modelStatusText
            },
            badgeLabel = if (isShieldRunning) stringResource(R.string.main_status_running) else stringResource(R.string.main_status_stopped),
            icon = if (isShieldRunning) Icons.Rounded.Security else Icons.Rounded.Shield,
        ) {
            when {
                !modelReady -> PrimaryActionButton(
                    label = stringResource(R.string.model_self_test_button_short),
                    icon = Icons.Rounded.Refresh,
                    onClick = onRunModelSelfTest,
                    isDestructive = true,
                )

                isShieldRunning -> PrimaryActionButton(
                    label = stringResource(R.string.shield_stop_button),
                    icon = Icons.Rounded.Stop,
                    onClick = onStopShield,
                    isDestructive = true,
                )

                else -> PrimaryActionButton(
                    label = stringResource(R.string.shield_start_button_short),
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onStartShield,
                )
            }
        }

        CompactCardGrid(
            first = {
                CompactInfoCard(
                    label = stringResource(R.string.shield_card_state),
                    value = if (isShieldRunning) stringResource(R.string.main_status_running) else stringResource(R.string.main_status_stopped),
                    icon = Icons.Rounded.Security,
                    isGood = isShieldRunning,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            second = {
                CompactInfoCard(
                    label = stringResource(R.string.shield_card_sensitivity),
                    value = stringResource(R.string.shield_threshold_value, thresholdPercent),
                    icon = Icons.Rounded.Tune,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            third = {
                CompactInfoCard(
                    label = stringResource(R.string.shield_card_blocked),
                    value = blockedImageCount.toString(),
                    icon = Icons.Rounded.WarningAmber,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            fourth = {
                CompactInfoCard(
                    label = stringResource(R.string.shield_card_last),
                    value = lastBlockedLabel,
                    icon = Icons.Rounded.Schedule,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )

        SectionCard(
            title = stringResource(R.string.shield_threshold_label_short),
            subtitle = stringResource(R.string.shield_threshold_hint),
            icon = Icons.Rounded.Tune,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Slider(
                    value = threshold,
                    onValueChange = onThresholdChange,
                    valueRange = thresholdRange,
                    onValueChangeFinished = onApplyThreshold,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.44f),
                    ),
                )
                SecondaryActionButton(
                    label = stringResource(R.string.shield_apply_threshold_short),
                    icon = Icons.Rounded.Tune,
                    onClick = onApplyThreshold,
                )
            }
        }

        SectionCard(
            title = stringResource(R.string.model_card_title_short),
            subtitle = modelStatusText,
            icon = Icons.Rounded.SmartToy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SecondaryActionButton(
                label = stringResource(R.string.model_self_test_button_short),
                icon = Icons.Rounded.Refresh,
                onClick = onRunModelSelfTest,
            )
        }
    }
}
