package com.aldrich.safeai.overlay

import android.app.Service
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.aldrich.safeai.MainActivity
import com.aldrich.safeai.R
import com.aldrich.safeai.capture.ScreenShieldForegroundService
import com.aldrich.safeai.ui.theme.ThemeMode

class SafetyOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                hideOverlay()
                stopSelf()
            }

            else -> {
                val reason = intent?.getStringExtra(EXTRA_REASON)
                showOverlay(reason)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun showOverlay(reason: String?) {
        if (overlayView != null) {
            return
        }

        val palette = resolveOverlayPalette()

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(palette.scrim)
            isClickable = true
            isFocusable = true
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dpToPx(24f), dpToPx(24f), dpToPx(24f), dpToPx(24f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(28f).toFloat()
                setColor(palette.card)
            }
            elevation = dpToPx(10f).toFloat()
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_overlay_shield)
            imageTintList = ColorStateList.valueOf(palette.icon)
            layoutParams = LinearLayout.LayoutParams(dpToPx(56f), dpToPx(56f)).apply {
                bottomMargin = dpToPx(12f)
            }
        }

        val title = TextView(this).apply {
            text = getString(R.string.overlay_block_title)
            textSize = 22f
            setTextColor(palette.title)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setLineSpacing(0f, 1.1f)
        }

        val body = TextView(this).apply {
            text = reason ?: getString(R.string.overlay_block_body)
            textSize = 15f
            setTextColor(palette.body)
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(14f), 0, dpToPx(22f))
            setLineSpacing(0f, 1.15f)
        }

        val actionColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        for (action in BlockedOverlayRecoveryAction.defaultActions()) {
            actionColumn.addView(createRecoveryButton(action, palette))
        }

        card.addView(icon)
        card.addView(title)
        card.addView(body)
        card.addView(actionColumn)

        root.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                marginStart = dpToPx(22f)
                marginEnd = dpToPx(22f)
            },
        )

        try {
            wm.addView(root, params)
            windowManager = wm
            overlayView = root
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
            .toInt()
    }

    private fun hideOverlay() {
        val wm = windowManager ?: return
        val view = overlayView ?: return

        runCatching { wm.removeView(view) }
        overlayView = null
        windowManager = null
    }

    private fun createRecoveryButton(
        action: BlockedOverlayRecoveryAction,
        palette: OverlayPalette,
    ): Button {
        return Button(this).apply {
            text = when (action) {
                BlockedOverlayRecoveryAction.OPEN_MAIN_MENU -> getString(R.string.overlay_main_menu)
                BlockedOverlayRecoveryAction.OPEN_HOME_TO_CLEAR_RECENTS -> getString(R.string.overlay_home_recents)
            }
            backgroundTintList = ColorStateList.valueOf(
                when (action) {
                    BlockedOverlayRecoveryAction.OPEN_MAIN_MENU -> palette.button
                    BlockedOverlayRecoveryAction.OPEN_HOME_TO_CLEAR_RECENTS -> palette.secondaryButton
                }
            )
            setTextColor(
                when (action) {
                    BlockedOverlayRecoveryAction.OPEN_MAIN_MENU -> palette.buttonText
                    BlockedOverlayRecoveryAction.OPEN_HOME_TO_CLEAR_RECENTS -> palette.secondaryButtonText
                }
            )
            setPadding(dpToPx(20f), dpToPx(8f), dpToPx(20f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dpToPx(8f)
            }
            setOnClickListener {
                recoverFromBlockedScreen(action)
            }
        }
    }

    private fun recoverFromBlockedScreen(action: BlockedOverlayRecoveryAction) {
        hideOverlay()
        when (action) {
            BlockedOverlayRecoveryAction.OPEN_MAIN_MENU -> openMainMenu()
            BlockedOverlayRecoveryAction.OPEN_HOME_TO_CLEAR_RECENTS -> openHomeScreen()
        }
        notifyShieldOverlayRecovered()
        stopSelf()
    }

    private fun notifyShieldOverlayRecovered() {
        val intent = Intent(this, ScreenShieldForegroundService::class.java).apply {
            action = ScreenShieldForegroundService.ACTION_OVERLAY_RECOVERED
        }
        startService(intent)
    }

    private fun openMainMenu() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(intent)
    }

    private fun openHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun resolveOverlayPalette(): OverlayPalette {
        val prefs = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val storedMode = ThemeMode.fromPreferenceValue(
            prefs.getString(PREFERENCE_KEY_THEME_MODE, ThemeMode.SYSTEM.preferenceValue),
        )

        val isDarkTheme = when (storedMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> {
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            }
        }

        return if (isDarkTheme) {
            OverlayPalette(
                scrim = Color.parseColor("#FF0C1526"),
                card = Color.parseColor("#FF1B273D"),
                icon = Color.parseColor("#FFB7C7FF"),
                title = Color.parseColor("#FFE1E8FB"),
                body = Color.parseColor("#FFC1CCE1"),
                button = Color.parseColor("#FFB7C7FF"),
                buttonText = Color.parseColor("#FF002A72"),
                secondaryButton = Color.parseColor("#FF26364F"),
                secondaryButtonText = Color.parseColor("#FFE1E8FB"),
            )
        } else {
            OverlayPalette(
                scrim = Color.parseColor("#FF121A30"),
                card = Color.parseColor("#FFF4F8FF"),
                icon = Color.parseColor("#FF1458D6"),
                title = Color.parseColor("#FF121C2F"),
                body = Color.parseColor("#FF3E4A63"),
                button = Color.parseColor("#FF1458D6"),
                buttonText = Color.WHITE,
                secondaryButton = Color.parseColor("#FFE3EAF8"),
                secondaryButtonText = Color.parseColor("#FF121C2F"),
            )
        }
    }

    private data class OverlayPalette(
        val scrim: Int,
        val card: Int,
        val icon: Int,
        val title: Int,
        val body: Int,
        val button: Int,
        val buttonText: Int,
        val secondaryButton: Int,
        val secondaryButtonText: Int,
    )

    companion object {
        const val ACTION_SHOW = "com.aldrich.safeai.overlay.SHOW"
        const val ACTION_HIDE = "com.aldrich.safeai.overlay.HIDE"
        const val EXTRA_REASON = "overlay_reason"
        private const val PREFERENCES_NAME = "safeai_preferences"
        private const val PREFERENCE_KEY_THEME_MODE = "app_theme_mode"
    }
}
