package com.aldrich.safeai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    isShieldRunning: Boolean,
    threshold: Float,
    blockedImageCount: Int,
    lastBlockedAtEpochMs: Long?,
    modelReady: Boolean,
    modelStatusText: String,
    modelStatusDetail: String?,
    isTestingModel: Boolean,
    onRunModelSelfTest: () -> Unit,
    onGoToShield: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thresholdPercent = (threshold * 100f).roundToInt()
    val hasBlockedBefore = remember(lastBlockedAtEpochMs) { lastBlockedAtEpochMs != null }

    ScreenColumn(modifier = modifier.fillMaxSize()) {
        ControlHeroCard(
            title = stringResource(R.string.dashboard_title_short),
            subtitle = if (isShieldRunning) {
                stringResource(R.string.dashboard_status_running_short)
            } else {
                stringResource(R.string.dashboard_status_stopped_short)
            },
            badgeLabel = stringResource(R.string.dashboard_hero_badge),
            icon = Icons.Rounded.Security,
        ) {
            PrimaryActionButton(
                label = stringResource(R.string.dashboard_manage_shield_short),
                icon = Icons.Rounded.PlayArrow,
                onClick = onGoToShield,
            )
        }

        CompactCardGrid(
            first = {
                CompactInfoCard(
                    label = stringResource(R.string.dashboard_card_shield),
                    value = if (isShieldRunning) stringResource(R.string.main_status_running) else stringResource(R.string.main_status_stopped),
                    icon = Icons.Rounded.Security,
                    isGood = isShieldRunning,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            second = {
                CompactInfoCard(
                    label = stringResource(R.string.dashboard_card_model),
                    value = if (modelReady) stringResource(R.string.dashboard_model_metric_ready) else stringResource(R.string.dashboard_model_metric_not_ready),
                    icon = Icons.Rounded.SmartToy,
                    isGood = modelReady,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            third = {
                CompactInfoCard(
                    label = stringResource(R.string.dashboard_card_sensitivity),
                    value = stringResource(R.string.dashboard_threshold_metric_value, thresholdPercent),
                    icon = Icons.Rounded.Tune,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            fourth = {
                CompactInfoCard(
                    label = stringResource(R.string.dashboard_card_blocked),
                    value = blockedImageCount.toString(),
                    icon = Icons.Rounded.WarningAmber,
                    isGood = !hasBlockedBefore,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )

        SectionCard(
            title = stringResource(R.string.dashboard_readiness_title),
            subtitle = stringResource(R.string.dashboard_readiness_body),
            icon = Icons.Rounded.Verified,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryActionButton(
                    label = if (isTestingModel) {
                        stringResource(R.string.model_self_test_running_short)
                    } else {
                        stringResource(R.string.model_self_test_button_short)
                    },
                    icon = Icons.Rounded.SmartToy,
                    onClick = onRunModelSelfTest,
                    enabled = !isTestingModel,
                )
                if (!modelStatusDetail.isNullOrBlank()) {
                    Text(
                        text = "$modelStatusText $modelStatusDetail",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
