package com.aldrich.safeai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aldrich.safeai.R

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(screenBackgroundBrush()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun SafeAiTopBar(
    title: String,
    subtitle: String,
    isShieldRunning: Boolean,
    onInfoClick: () -> Unit,
    onSettingsClick: () -> Unit,
    infoIcon: ImageVector,
    settingsIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val statusLabel = if (isShieldRunning) {
        stringResource(R.string.main_status_running)
    } else {
        stringResource(R.string.main_status_stopped)
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .background(screenBackgroundBrush())
                .statusBarsPadding()
                .padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.extraLarge,
                border = glassBorder(),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardGradientBrush(0.46f)),
                ) {
                    DecorativeRingField(
                        modifier = Modifier.matchParentSize(),
                        intensity = 0.42f,
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                                        ),
                                    ),
                                )
                                .border(glassBorder(), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_safeai_mark),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "$subtitle - $statusLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            CircleIconButton(icon = infoIcon, contentDescription = stringResource(R.string.main_tab_about), onClick = onInfoClick)
            CircleIconButton(icon = settingsIcon, contentDescription = stringResource(R.string.main_tab_setup), onClick = onSettingsClick)
        }
    }
}

@Composable
fun SafeAiNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(screenBackgroundBrush())
                .navigationBarsPadding()
                .padding(top = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            val navShape = RoundedCornerShape(
                topStart = 8.dp,
                topEnd = 8.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            )
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(navShape)
                    .background(cardGradientBrush(0.48f))
                    .border(glassBorder(), navShape),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.84f),
                tonalElevation = 0.dp,
                content = content,
            )
        }
    }
}

@Composable
fun ShieldStatusChip(
    isShieldRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    StatusPill(
        label = if (isShieldRunning) {
            stringResource(R.string.main_status_running)
        } else {
            stringResource(R.string.main_status_stopped)
        },
        icon = if (isShieldRunning) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
        containerColor = if (isShieldRunning) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.86f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f)
        },
        contentColor = if (isShieldRunning) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

@Composable
fun ControlHeroCard(
    title: String,
    subtitle: String,
    badgeLabel: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val cardShape = decorativeCardShape("hero-$title", prominent = true)
    val visualSeed = visualSeed("hero-$title")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)),
        border = glassBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroGradientBrush(visualSeed)),
        ) {
            DecorativeRingField(
                modifier = Modifier.matchParentSize(),
                intensity = 0.86f,
                variant = visualSeed,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatusPill(label = badgeLabel)
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconTile(
                        icon = icon,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.36f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(58.dp),
                    )
                }
                content()
            }
        }
    }
}

@Composable
fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(horizontal = 18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryActionButton(
    label: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(9.dp))
        }
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
) {
    val cardShape = decorativeCardShape("metric-$label-$value")
    val visualSeed = visualSeed("metric-$label-$value")

    Card(
        modifier = modifier.heightIn(min = 118.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)),
        border = glassBorder(0.16f),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradientBrush(0.54f, visualSeed)),
        ) {
            DecorativeRingField(
                modifier = Modifier.matchParentSize(),
                intensity = 0.44f,
                variant = visualSeed,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!supportingText.isNullOrBlank()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ResponsiveTwoColumn(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                first()
                second()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) { first() }
                Box(modifier = Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
fun CompactCardGrid(
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    third: @Composable () -> Unit,
    fourth: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) { first() }
                    Box(modifier = Modifier.weight(1f)) { second() }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) { third() }
                    Box(modifier = Modifier.weight(1f)) { fourth() }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) { first() }
                Box(modifier = Modifier.weight(1f)) { second() }
                Box(modifier = Modifier.weight(1f)) { third() }
                Box(modifier = Modifier.weight(1f)) { fourth() }
            }
        }
    }
}

@Composable
fun CompactInfoCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isGood: Boolean? = null,
) {
    val iconContainer = when (isGood) {
        true -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f)
        false -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.78f)
        null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    }
    val iconContent = when (isGood) {
        true -> MaterialTheme.colorScheme.onSecondaryContainer
        false -> MaterialTheme.colorScheme.onTertiaryContainer
        null -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val cardShape = decorativeCardShape("compact-$label-$value")
    val visualSeed = visualSeed("compact-$label-$value")

    Card(
        modifier = modifier.heightIn(min = 116.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.66f)),
        border = glassBorder(0.14f),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradientBrush(0.58f, visualSeed)),
        ) {
            DecorativeRingField(
                modifier = Modifier.matchParentSize(),
                intensity = 0.52f,
                variant = visualSeed,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconTile(
                    icon = icon,
                    containerColor = iconContainer,
                    contentColor = iconContent,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val cardShape = decorativeCardShape("section-$title")
    val visualSeed = visualSeed("section-$title")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = glassBorder(0.14f),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradientBrush(0.50f, visualSeed)),
        ) {
            DecorativeRingField(
                modifier = Modifier.matchParentSize(),
                intensity = 0.46f,
                variant = visualSeed,
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (icon != null) {
                        IconTile(
                            icon = icon,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                content()
            }
        }
    }
}

@Composable
fun ReadinessRow(
    title: String,
    body: String,
    isComplete: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.30f))
            .border(glassBorder(0.12f), MaterialTheme.shapes.extraLarge)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(
            icon = if (isComplete) Icons.Rounded.CheckCircle else icon,
            containerColor = if (isComplete) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = if (isComplete) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(36.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusPill(
            label = if (isComplete) stringResource(R.string.setup_permission_granted) else stringResource(R.string.setup_permission_required),
            containerColor = if (isComplete) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.82f),
            contentColor = if (isComplete) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
fun ProgressSummary(
    completedSteps: Int,
    totalSteps: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val progress = if (totalSteps == 0) 0f else completedSteps.toFloat() / totalSteps.toFloat()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "$completedSteps/$totalSteps", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.small),
            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = title,
        subtitle = body,
        icon = icon,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.66f),
    ) {
        if (actionLabel != null && onAction != null) {
            PrimaryActionButton(label = actionLabel, icon = icon, onClick = onAction)
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    isGranted: Boolean,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = title,
        subtitle = description,
        icon = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
        modifier = modifier,
        containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.66f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusPill(
                label = if (isGranted) stringResource(R.string.setup_permission_granted) else stringResource(R.string.setup_permission_required),
                containerColor = if (isGranted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = if (isGranted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.weight(1f))
            SecondaryActionButton(
                label = actionLabel,
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = glassBorder(),
        modifier = Modifier.size(54.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        modifier = modifier.size(42.dp),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        border = glassBorder(0.10f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        border = glassBorder(0.22f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(15.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun screenBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.10f),
            MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun heroGradientBrush(variant: Int): Brush {
    val colors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.26f),
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f),
    )
    return Brush.linearGradient(
        colors = rotateColors(colors, variant),
        start = gradientStart(variant),
        end = gradientEnd(variant),
    )
}

@Composable
private fun cardGradientBrush(strength: Float, variant: Int = 0): Brush {
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = strength),
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = strength * 0.30f),
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = strength * 0.22f),
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = strength * 0.16f),
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = strength * 0.78f),
    )
    return Brush.linearGradient(
        colors = rotateColors(colors, variant),
        start = gradientStart(variant),
        end = gradientEnd(variant),
    )
}

@Composable
private fun DecorativeRingField(
    modifier: Modifier = Modifier,
    intensity: Float = 0.5f,
    variant: Int = 0,
) {
    val primary = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f * intensity)
    val secondary = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f * intensity)
    val tertiary = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f * intensity)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f * intensity)

    Canvas(modifier = modifier) {
        val thinStroke = Stroke(width = 1.2.dp.toPx())
        val boldStroke = Stroke(width = 1.8.dp.toPx())
        val minSide = size.minDimension
        val pattern = ringPattern(variant)

        drawCircle(
            color = primary,
            radius = minSide * pattern.primaryRadius,
            center = Offset(size.width * pattern.primaryX, size.height * pattern.primaryY),
            style = boldStroke,
        )
        drawCircle(
            color = secondary,
            radius = minSide * pattern.secondaryRadius,
            center = Offset(size.width * pattern.secondaryX, size.height * pattern.secondaryY),
            style = thinStroke,
        )
        drawCircle(
            color = tertiary,
            radius = minSide * pattern.tertiaryRadius,
            center = Offset(size.width * pattern.tertiaryX, size.height * pattern.tertiaryY),
            style = thinStroke,
        )
        drawCircle(
            color = outline,
            radius = minSide * pattern.outlineRadius,
            center = Offset(size.width * pattern.outlineX, size.height * pattern.outlineY),
            style = thinStroke,
        )
    }
}

@Composable
private fun glassBorder(alpha: Float = 0.24f): BorderStroke {
    return BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = alpha))
}

private fun decorativeCardShape(
    seed: String,
    prominent: Boolean = false,
): Shape {
    val max = if (prominent) 8.dp else 7.dp
    val high = if (prominent) 8.dp else 6.dp
    val mid = if (prominent) 6.dp else 5.dp
    val low = if (prominent) 4.dp else 3.dp

    return when ((seed.hashCode() and Int.MAX_VALUE) % 5) {
        0 -> RoundedCornerShape(
            topStart = max,
            topEnd = mid,
            bottomEnd = high,
            bottomStart = low,
        )

        1 -> RoundedCornerShape(
            topStart = mid,
            topEnd = max,
            bottomEnd = low,
            bottomStart = high,
        )

        2 -> RoundedCornerShape(
            topStart = high,
            topEnd = low,
            bottomEnd = max,
            bottomStart = mid,
        )

        3 -> RoundedCornerShape(
            topStart = low,
            topEnd = high,
            bottomEnd = mid,
            bottomStart = max,
        )

        else -> RoundedCornerShape(
            topStart = max,
            topEnd = high,
            bottomEnd = low,
            bottomStart = mid,
        )
    }
}

private fun visualSeed(seed: String): Int {
    return seed.hashCode() and Int.MAX_VALUE
}

private fun rotateColors(colors: List<Color>, variant: Int): List<Color> {
    if (colors.isEmpty()) return colors
    val shift = variant % colors.size
    return colors.drop(shift) + colors.take(shift)
}

private fun gradientStart(variant: Int): Offset {
    return when (variant % 4) {
        0 -> Offset.Zero
        1 -> Offset(900f, 0f)
        2 -> Offset(0f, 900f)
        else -> Offset(900f, 900f)
    }
}

private fun gradientEnd(variant: Int): Offset {
    return when (variant % 4) {
        0 -> Offset(900f, 900f)
        1 -> Offset(0f, 900f)
        2 -> Offset(900f, 0f)
        else -> Offset.Zero
    }
}

private data class RingPattern(
    val primaryX: Float,
    val primaryY: Float,
    val primaryRadius: Float,
    val secondaryX: Float,
    val secondaryY: Float,
    val secondaryRadius: Float,
    val tertiaryX: Float,
    val tertiaryY: Float,
    val tertiaryRadius: Float,
    val outlineX: Float,
    val outlineY: Float,
    val outlineRadius: Float,
)

private fun ringPattern(variant: Int): RingPattern {
    return when (variant % 6) {
        0 -> RingPattern(
            primaryX = 0.94f,
            primaryY = 0.12f,
            primaryRadius = 0.33f,
            secondaryX = 0.88f,
            secondaryY = 0.88f,
            secondaryRadius = 0.24f,
            tertiaryX = 0.08f,
            tertiaryY = 0.12f,
            tertiaryRadius = 0.18f,
            outlineX = 1.04f,
            outlineY = 0.58f,
            outlineRadius = 0.52f,
        )

        1 -> RingPattern(
            primaryX = 0.08f,
            primaryY = 0.88f,
            primaryRadius = 0.31f,
            secondaryX = 0.96f,
            secondaryY = 0.18f,
            secondaryRadius = 0.22f,
            tertiaryX = 0.78f,
            tertiaryY = 0.96f,
            tertiaryRadius = 0.17f,
            outlineX = -0.04f,
            outlineY = 0.36f,
            outlineRadius = 0.48f,
        )

        2 -> RingPattern(
            primaryX = 0.98f,
            primaryY = 0.72f,
            primaryRadius = 0.29f,
            secondaryX = 0.18f,
            secondaryY = 0.16f,
            secondaryRadius = 0.25f,
            tertiaryX = 0.18f,
            tertiaryY = 0.94f,
            tertiaryRadius = 0.16f,
            outlineX = 0.68f,
            outlineY = -0.08f,
            outlineRadius = 0.46f,
        )

        3 -> RingPattern(
            primaryX = 0.52f,
            primaryY = -0.06f,
            primaryRadius = 0.34f,
            secondaryX = 0.04f,
            secondaryY = 0.64f,
            secondaryRadius = 0.21f,
            tertiaryX = 0.98f,
            tertiaryY = 0.88f,
            tertiaryRadius = 0.19f,
            outlineX = 0.82f,
            outlineY = 0.22f,
            outlineRadius = 0.43f,
        )

        4 -> RingPattern(
            primaryX = -0.02f,
            primaryY = 0.22f,
            primaryRadius = 0.32f,
            secondaryX = 0.64f,
            secondaryY = 0.98f,
            secondaryRadius = 0.23f,
            tertiaryX = 0.94f,
            tertiaryY = 0.08f,
            tertiaryRadius = 0.15f,
            outlineX = 1.02f,
            outlineY = 0.62f,
            outlineRadius = 0.50f,
        )

        else -> RingPattern(
            primaryX = 0.80f,
            primaryY = 0.92f,
            primaryRadius = 0.30f,
            secondaryX = 0.08f,
            secondaryY = 0.08f,
            secondaryRadius = 0.20f,
            tertiaryX = 0.48f,
            tertiaryY = 0.18f,
            tertiaryRadius = 0.17f,
            outlineX = 0.16f,
            outlineY = 0.76f,
            outlineRadius = 0.44f,
        )
    }
}
