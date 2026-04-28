package com.aldrich.safeai.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aldrich.safeai.R
import com.aldrich.safeai.ui.components.CompactCardGrid
import com.aldrich.safeai.ui.components.CompactInfoCard
import com.aldrich.safeai.ui.components.ControlHeroCard
import com.aldrich.safeai.ui.components.ScreenColumn
import com.aldrich.safeai.ui.components.SectionCard

@Composable
fun AboutScreen(
    appVersionLabel: String,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier = modifier.fillMaxSize()) {
        ControlHeroCard(
            title = stringResource(R.string.about_title_short),
            subtitle = stringResource(R.string.about_tagline_short),
            badgeLabel = stringResource(R.string.about_version, appVersionLabel),
            icon = Icons.Rounded.Security,
        )

        CompactCardGrid(
            first = {
                CompactInfoCard(
                    label = stringResource(R.string.about_card_privacy),
                    value = stringResource(R.string.about_card_local),
                    icon = Icons.Rounded.Lock,
                    isGood = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            second = {
                CompactInfoCard(
                    label = stringResource(R.string.about_card_model),
                    value = stringResource(R.string.about_card_ai),
                    icon = Icons.Rounded.SmartToy,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            third = {
                CompactInfoCard(
                    label = stringResource(R.string.about_card_shield),
                    value = stringResource(R.string.about_card_realtime),
                    icon = Icons.Rounded.Security,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            fourth = {
                CompactInfoCard(
                    label = stringResource(R.string.about_card_version),
                    value = appVersionLabel,
                    icon = Icons.Rounded.Tag,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )

        SectionCard(
            title = stringResource(R.string.about_mission_title),
            subtitle = stringResource(R.string.about_mission_body),
            icon = Icons.Rounded.Info,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionCard(
            title = stringResource(R.string.about_note_title),
            subtitle = stringResource(R.string.about_support_body_short),
            icon = Icons.Rounded.Security,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
