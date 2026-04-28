package com.aldrich.safeai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.Info as OutlinedInfo
import androidx.compose.material.icons.outlined.Security as OutlinedSecurity
import androidx.compose.material.icons.outlined.Settings as OutlinedSettings
import androidx.compose.material.icons.rounded.Home as RoundedHome
import androidx.compose.material.icons.rounded.Info as RoundedInfo
import androidx.compose.material.icons.rounded.Security as RoundedSecurity
import androidx.compose.material.icons.rounded.Settings as RoundedSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.aldrich.safeai.capture.ScreenShieldForegroundService
import com.aldrich.safeai.data.ShieldStatsStore
import com.aldrich.safeai.inference.ModelSelfTest
import com.aldrich.safeai.ui.components.SafeAiNavigationBar
import com.aldrich.safeai.ui.components.SafeAiTopBar
import com.aldrich.safeai.ui.screens.AboutScreen
import com.aldrich.safeai.ui.screens.DashboardScreen
import com.aldrich.safeai.ui.screens.SetupScreen
import com.aldrich.safeai.ui.screens.ShieldScreen
import com.aldrich.safeai.ui.theme.SafeAITheme
import com.aldrich.safeai.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafeAIApp()
        }
    }
}

private enum class MainMenuTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DASHBOARD(
        labelRes = R.string.main_tab_dashboard,
        selectedIcon = Icons.Rounded.RoundedHome,
        unselectedIcon = Icons.Outlined.OutlinedHome,
    ),
    SHIELD(
        labelRes = R.string.main_tab_shield,
        selectedIcon = Icons.Rounded.RoundedSecurity,
        unselectedIcon = Icons.Outlined.OutlinedSecurity,
    ),
    SETUP(
        labelRes = R.string.main_tab_setup,
        selectedIcon = Icons.Rounded.RoundedSettings,
        unselectedIcon = Icons.Outlined.OutlinedSettings,
    ),
    ABOUT(
        labelRes = R.string.main_tab_about,
        selectedIcon = Icons.Rounded.RoundedInfo,
        unselectedIcon = Icons.Outlined.OutlinedInfo,
    ),
}

@Composable
fun SafeAIApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val prefs = remember(context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var themeMode by rememberSaveable {
        mutableStateOf(
            ThemeMode.fromPreferenceValue(
                prefs.getString(PREFERENCE_KEY_THEME_MODE, ThemeMode.SYSTEM.preferenceValue),
            ),
        )
    }

    var selectedTabName by rememberSaveable { mutableStateOf(MainMenuTab.DASHBOARD.name) }
    val selectedTab = runCatching { MainMenuTab.valueOf(selectedTabName) }
        .getOrDefault(MainMenuTab.DASHBOARD)

    var threshold by rememberSaveable {
        mutableStateOf(prefs.getFloat(PREFERENCE_KEY_THRESHOLD, DEFAULT_THRESHOLD))
    }
    var blockedImageCount by remember { mutableIntStateOf(0) }
    var lastBlockedAtEpochMs by remember { mutableStateOf<Long?>(null) }
    var isShieldRunning by remember { mutableStateOf(ScreenShieldForegroundService.isRunning) }
    var hasOverlayPermission by remember { mutableStateOf(hasOverlayPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(hasNotificationPermission(context)) }

    var modelReady by remember { mutableStateOf(false) }
    var modelStatusText by remember { mutableStateOf(context.getString(R.string.model_status_checking)) }
    var modelStatusDetail by remember { mutableStateOf<String?>(null) }
    var isTestingModel by remember { mutableStateOf(false) }

    suspend fun refreshModelStatus(showSnackbar: Boolean) {
        isTestingModel = true
        modelStatusText = context.getString(R.string.model_status_checking)
        modelStatusDetail = null

        val result = withContext(Dispatchers.Default) {
            ModelSelfTest.run(context)
        }

        if (result.isReady) {
            modelReady = true
            val modelSizeKb = (result.modelBytes / 1024f).roundToInt().coerceAtLeast(1)
            modelStatusText = context.getString(R.string.model_status_ready, modelSizeKb)
            modelStatusDetail = null
            if (showSnackbar) {
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_model_ready))
            }
        } else {
            modelReady = false
            modelStatusText = context.getString(R.string.model_status_not_ready)
            modelStatusDetail = result.errorMessage
            if (showSnackbar) {
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_model_not_ready))
            }
        }

        isTestingModel = false
    }

    fun refreshShieldStats() {
        val stats = ShieldStatsStore.read(context)
        blockedImageCount = stats.blockedImageCount
        lastBlockedAtEpochMs = stats.lastBlockedAtEpochMs
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted
        val message = if (granted) {
            context.getString(R.string.snackbar_notification_granted)
        } else {
            context.getString(R.string.snackbar_notification_denied)
        }
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val projectionData = result.data
        if (result.resultCode == Activity.RESULT_OK && projectionData != null) {
            startShieldService(
                context = context,
                resultCode = result.resultCode,
                projectionData = projectionData,
                threshold = threshold,
            )
            isShieldRunning = true
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_shield_started))
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_shield_permission_cancelled),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshModelStatus(showSnackbar = false)
        refreshShieldStats()

        while (true) {
            isShieldRunning = ScreenShieldForegroundService.isRunning
            hasOverlayPermission = hasOverlayPermission(context)
            hasNotificationPermission = hasNotificationPermission(context)
            refreshShieldStats()
            delay(POLLING_INTERVAL_MS)
        }
    }

    val onRunModelSelfTest: () -> Unit = {
        scope.launch {
            refreshModelStatus(showSnackbar = true)
        }
    }

    val onStartShieldRequested: () -> Unit = startAction@{
        if (!modelReady) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_model_missing_cannot_start),
                )
            }
            selectedTabName = MainMenuTab.DASHBOARD.name
            return@startAction
        }

        if (!hasOverlayPermission) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_overlay_required))
            }
            selectedTabName = MainMenuTab.SETUP.name
            openOverlaySettings(context)
            return@startAction
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    SafeAITheme(
        themeMode = themeMode,
        dynamicColor = false,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                SafeAiTopBar(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.main_header_subtitle),
                    isShieldRunning = isShieldRunning,
                    onInfoClick = { selectedTabName = MainMenuTab.ABOUT.name },
                    onSettingsClick = { selectedTabName = MainMenuTab.SETUP.name },
                    infoIcon = Icons.Rounded.RoundedInfo,
                    settingsIcon = Icons.Rounded.RoundedSettings,
                )
            },
            bottomBar = {
                SafeAiNavigationBar {
                    MainMenuTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTabName = tab.name },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes),
                                )
                            },
                            label = {
                                Text(text = stringResource(tab.labelRes))
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
        ) { innerPadding ->
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "safeai-tab-content",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { tab ->
                when (tab) {
                    MainMenuTab.DASHBOARD -> DashboardScreen(
                        isShieldRunning = isShieldRunning,
                        threshold = threshold,
                        blockedImageCount = blockedImageCount,
                        lastBlockedAtEpochMs = lastBlockedAtEpochMs,
                        modelReady = modelReady,
                        modelStatusText = modelStatusText,
                        modelStatusDetail = modelStatusDetail,
                        isTestingModel = isTestingModel,
                        onRunModelSelfTest = onRunModelSelfTest,
                        onGoToShield = { selectedTabName = MainMenuTab.SHIELD.name },
                        modifier = Modifier.fillMaxSize(),
                    )

                    MainMenuTab.SHIELD -> ShieldScreen(
                        isShieldRunning = isShieldRunning,
                        threshold = threshold,
                        thresholdRange = THRESHOLD_MIN..THRESHOLD_MAX,
                        blockedImageCount = blockedImageCount,
                        lastBlockedAtEpochMs = lastBlockedAtEpochMs,
                        modelReady = modelReady,
                        modelStatusText = modelStatusText,
                        onRunModelSelfTest = onRunModelSelfTest,
                        onThresholdChange = { threshold = it.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX) },
                        onApplyThreshold = {
                            saveThreshold(prefs, threshold)
                            if (isShieldRunning) {
                                sendThresholdUpdate(context, threshold)
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.snackbar_threshold_applied),
                                )
                            }
                        },
                        onStartShield = onStartShieldRequested,
                        onStopShield = {
                            stopShieldService(context)
                            isShieldRunning = false
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.snackbar_shield_stopped),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    MainMenuTab.SETUP -> SetupScreen(
                        hasOverlayPermission = hasOverlayPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        modelReady = modelReady,
                        modelStatusText = modelStatusText,
                        themeMode = themeMode,
                        onThemeModeChange = {
                            themeMode = it
                            saveThemeMode(prefs, it)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        R.string.snackbar_theme_applied,
                                        context.getString(it.labelRes),
                                    ),
                                )
                            }
                        },
                        onRunModelSelfTest = onRunModelSelfTest,
                        onOpenOverlaySettings = {
                            openOverlaySettings(context)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.snackbar_opened_overlay_settings),
                                )
                            }
                        },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    MainMenuTab.ABOUT -> AboutScreen(
                        appVersionLabel = APP_VERSION_LABEL,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun startShieldService(
    context: Context,
    resultCode: Int,
    projectionData: Intent,
    threshold: Float,
) {
    val intent = Intent(context, ScreenShieldForegroundService::class.java).apply {
        action = ScreenShieldForegroundService.ACTION_START
        putExtra(ScreenShieldForegroundService.EXTRA_RESULT_CODE, resultCode)
        putExtra(ScreenShieldForegroundService.EXTRA_RESULT_DATA, projectionData)
        putExtra(ScreenShieldForegroundService.EXTRA_THRESHOLD, threshold)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.startForegroundService(context, intent)
    } else {
        context.startService(intent)
    }
}

private fun stopShieldService(context: Context) {
    val intent = Intent(context, ScreenShieldForegroundService::class.java).apply {
        action = ScreenShieldForegroundService.ACTION_STOP
    }
    context.startService(intent)
}

private fun sendThresholdUpdate(context: Context, threshold: Float) {
    val intent = Intent(context, ScreenShieldForegroundService::class.java).apply {
        action = ScreenShieldForegroundService.ACTION_UPDATE_THRESHOLD
        putExtra(ScreenShieldForegroundService.EXTRA_THRESHOLD, threshold)
    }
    context.startService(intent)
}

private fun hasOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true
    }

    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun saveThreshold(prefs: SharedPreferences, threshold: Float) {
    prefs.edit {
        putFloat(PREFERENCE_KEY_THRESHOLD, threshold.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX))
    }
}

private fun saveThemeMode(prefs: SharedPreferences, themeMode: ThemeMode) {
    prefs.edit {
        putString(PREFERENCE_KEY_THEME_MODE, themeMode.preferenceValue)
    }
}

private const val PREFERENCES_NAME = "safeai_preferences"
private const val PREFERENCE_KEY_THRESHOLD = "screen_shield_threshold"
private const val PREFERENCE_KEY_THEME_MODE = "app_theme_mode"
private const val DEFAULT_THRESHOLD = 0.55f
private const val THRESHOLD_MIN = 0.20f
private const val THRESHOLD_MAX = 0.95f
private const val POLLING_INTERVAL_MS = 1000L
private const val APP_VERSION_LABEL = "1.0"
