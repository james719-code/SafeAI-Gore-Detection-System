package com.aldrich.safeai.capture

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aldrich.safeai.MainActivity
import com.aldrich.safeai.R
import com.aldrich.safeai.data.ShieldStatsStore
import com.aldrich.safeai.inference.GoreClassifier
import com.aldrich.safeai.inference.GoreVisualHeuristic
import com.aldrich.safeai.inference.SafetyEvaluator
import com.aldrich.safeai.inference.SafetyLevel
import com.aldrich.safeai.overlay.SafetyOverlayService
import kotlin.math.roundToInt

class ScreenShieldForegroundService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var classifier: GoreClassifier? = null

    private var analysisThread: HandlerThread? = null
    private var analysisHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var threshold: Float = DEFAULT_THRESHOLD

    @Volatile
    private var lastScanTimestampMs: Long = 0L

    @Volatile
    private var isAnalyzingFrame = false

    private var hasShownBlockedOverlay = false
    private var suppressBlockedOverlayUntilMs = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post {
                updateNotification(getString(R.string.shield_status_projection_stopped))
                stopShieldMode()
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopShieldMode()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE_THRESHOLD -> {
                val newThreshold = intent.getFloatExtra(EXTRA_THRESHOLD, threshold)
                threshold = newThreshold.coerceIn(0f, 1f)
                updateNotification(
                    getString(
                        R.string.shield_status_running_threshold,
                        threshold,
                    )
                )
                return START_STICKY
            }

            ACTION_OVERLAY_RECOVERED -> {
                startOverlayRecoveryWindow()
                return START_STICKY
            }

            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val projectionIntent = intent.readProjectionIntentExtra()
                val requestedThreshold = intent.getFloatExtra(EXTRA_THRESHOLD, DEFAULT_THRESHOLD)
                threshold = requestedThreshold.coerceIn(0f, 1f)

                if (resultCode != Activity.RESULT_OK || projectionIntent == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                startShieldMode(resultCode, projectionIntent)
                return START_STICKY
            }

            else -> {
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopShieldMode()
        super.onDestroy()
    }

    private fun startShieldMode(resultCode: Int, projectionIntent: Intent) {
        if (isRunning) {
            updateNotification(
                getString(
                    R.string.shield_status_running_threshold,
                    threshold,
                )
            )
            return
        }

        createNotificationChannel()
        startForegroundWithType(buildNotification(getString(R.string.shield_status_starting)))
        isRunning = true

        val modelResult = runCatching { GoreClassifier(this) }
        classifier = modelResult.getOrNull()
        if (classifier == null) {
            updateNotification(getString(R.string.shield_status_model_missing))
            stopShieldMode()
            stopSelf()
            return
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = runCatching { manager.getMediaProjection(resultCode, projectionIntent) }.getOrNull()
        if (projection == null) {
            updateNotification(getString(R.string.shield_status_projection_error))
            stopShieldMode()
            stopSelf()
            return
        }

        analysisThread = HandlerThread("screen-shield-analysis").also { thread ->
            thread.start()
            analysisHandler = Handler(thread.looper)
        }

        mediaProjection = projection
        mediaProjection?.registerCallback(projectionCallback, analysisHandler)

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val densityDpi = metrics.densityDpi.coerceAtLeast(1)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            analyzeFrame(reader)
        }, analysisHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            analysisHandler,
        )

        if (virtualDisplay == null) {
            updateNotification(getString(R.string.shield_status_projection_error))
            stopShieldMode()
            stopSelf()
            return
        }

        updateNotification(
            getString(
                R.string.shield_status_running_threshold,
                threshold,
            )
        )
    }

    private fun analyzeFrame(reader: ImageReader) {
        if (isAnalyzingFrame) {
            reader.acquireLatestImage()?.close()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastScanTimestampMs < FRAME_SCAN_INTERVAL_MS) {
            reader.acquireLatestImage()?.close()
            return
        }

        val image = reader.acquireLatestImage() ?: return
        isAnalyzingFrame = true

        try {
            lastScanTimestampMs = now
            val bitmap = image.toBitmap() ?: return
            val scaled = bitmap.scaleForScreenScan()
            if (scaled !== bitmap) {
                bitmap.recycle()
            }

            val localClassifier = classifier ?: return
            val goreProbability = localClassifier.scanForMaxGoreProbability(scaled, threshold)
            if (!scaled.isRecycled) {
                scaled.recycle()
            }

            val assessment = SafetyEvaluator.assess(goreProbability, threshold)
            when (assessment.level) {
                SafetyLevel.BLOCKED -> {
                    if (
                        shouldShowBlockedOverlay(
                            hasShownBlockedOverlay = hasShownBlockedOverlay,
                            nowMs = now,
                            suppressBlockedOverlayUntilMs = suppressBlockedOverlayUntilMs,
                        )
                    ) {
                        showSafetyOverlay(getString(R.string.overlay_reason_live_shield_blocked))
                        hasShownBlockedOverlay = true
                        ShieldStatsStore.recordBlockedTransition(applicationContext)
                    }
                    if (hasShownBlockedOverlay) {
                        updateNotification(
                            getString(
                                R.string.shield_status_blocked_probability,
                                assessment.goreProbability,
                            )
                        )
                    } else {
                        updateNotification(
                            getString(
                                R.string.shield_status_running_threshold,
                                threshold,
                            )
                        )
                    }
                }

                SafetyLevel.SAFE -> {
                    if (hasShownBlockedOverlay && shouldHideBlockedOverlay(assessment.level)) {
                        hideSafetyOverlay()
                        hasShownBlockedOverlay = false
                        updateNotification(
                            getString(
                                R.string.shield_status_running_threshold,
                                threshold,
                            )
                        )
                    } else if (!hasShownBlockedOverlay) {
                        updateNotification(
                            getString(
                                R.string.shield_status_running_threshold,
                                threshold,
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            updateNotification(getString(R.string.shield_status_scan_error))
        } finally {
            image.close()
            isAnalyzingFrame = false
        }
    }

    private fun stopShieldMode() {
        hideSafetyOverlay()
        hasShownBlockedOverlay = false
        suppressBlockedOverlayUntilMs = 0L

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { imageReader?.close() }
        imageReader = null

        runCatching {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        }
        mediaProjection = null

        runCatching { classifier?.close() }
        classifier = null

        runCatching {
            analysisThread?.quitSafely()
        }
        analysisThread = null
        analysisHandler = null
        isAnalyzingFrame = false
        isRunning = false

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun showSafetyOverlay(reason: String) {
        val intent = Intent(this, SafetyOverlayService::class.java).apply {
            action = SafetyOverlayService.ACTION_SHOW
            putExtra(SafetyOverlayService.EXTRA_REASON, reason)
        }
        startService(intent)
    }

    private fun hideSafetyOverlay() {
        val intent = Intent(this, SafetyOverlayService::class.java).apply {
            action = SafetyOverlayService.ACTION_HIDE
        }
        startService(intent)
    }

    private fun startOverlayRecoveryWindow() {
        hasShownBlockedOverlay = false
        suppressBlockedOverlayUntilMs = SystemClock.elapsedRealtime() + OVERLAY_RECOVERY_SUPPRESSION_MS
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, ScreenShieldForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle(getString(R.string.shield_notification_title))
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.shield_notification_stop_action),
                stopPendingIntent,
            )
            .build()
    }

    @SuppressLint("MissingPermission", "NotificationPermission")
    private fun updateNotification(statusText: String) {
        if (!isRunning && statusText != getString(R.string.shield_status_starting)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionState = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        runCatching {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                buildNotification(statusText),
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.shield_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.shield_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun Intent.readProjectionIntentExtra(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private fun Image.toBitmap(): Bitmap? {
        val plane = planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val paddedBitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888,
        )
        paddedBitmap.copyPixelsFromBuffer(buffer)

        val croppedBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
        if (croppedBitmap !== paddedBitmap) {
            paddedBitmap.recycle()
        }
        return croppedBitmap
    }

    private fun Bitmap.scaleForScreenScan(): Bitmap {
        val maxDimension = maxOf(width, height)
        if (maxDimension <= MAX_SCAN_DIMENSION) {
            return this
        }

        val scale = MAX_SCAN_DIMENSION.toFloat() / maxDimension.toFloat()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun GoreClassifier.scanForMaxGoreProbability(
        bitmap: Bitmap,
        threshold: Float,
    ): Float {
        var maxGoreProbability = 0f
        val regions = ScreenScanRegions.create(bitmap.width, bitmap.height)

        for (region in regions) {
            val regionBitmap = if (region.covers(bitmap.width, bitmap.height)) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, region.left, region.top, region.width, region.height)
            }

            try {
                if (regionBitmap.hasLikelyGoreVisuals()) {
                    return threshold
                }

                val prediction = classify(regionBitmap, threshold)
                maxGoreProbability = maxOf(maxGoreProbability, prediction.goreProbability)
                if (maxGoreProbability >= threshold) {
                    return maxGoreProbability
                }
            } finally {
                if (regionBitmap !== bitmap && !regionBitmap.isRecycled) {
                    regionBitmap.recycle()
                }
            }
        }

        return maxGoreProbability
    }

    private fun Bitmap.hasLikelyGoreVisuals(): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return GoreVisualHeuristic.isLikelyGore(width, height, pixels)
    }

    companion object {
        const val ACTION_START = "com.aldrich.safeai.capture.START"
        const val ACTION_STOP = "com.aldrich.safeai.capture.STOP"
        const val ACTION_UPDATE_THRESHOLD = "com.aldrich.safeai.capture.UPDATE_THRESHOLD"
        const val ACTION_OVERLAY_RECOVERED = "com.aldrich.safeai.capture.OVERLAY_RECOVERED"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_THRESHOLD = "extra_threshold"

        @Volatile
        var isRunning: Boolean = false
            private set

        private const val NOTIFICATION_ID = 10051
        private const val NOTIFICATION_CHANNEL_ID = "safeai_screen_shield"
        private const val VIRTUAL_DISPLAY_NAME = "SafeAI_ScreenShield"
        private const val FRAME_SCAN_INTERVAL_MS = 900L
        private const val OVERLAY_RECOVERY_SUPPRESSION_MS = 8_000L
        private const val MAX_SCAN_DIMENSION = 1280
        private const val DEFAULT_THRESHOLD = 0.55f

        internal fun shouldHideBlockedOverlay(level: SafetyLevel): Boolean = when (level) {
            SafetyLevel.BLOCKED -> false
            SafetyLevel.SAFE -> false
        }

        internal fun shouldResetBlockedOverlayStateAfterRecovery(): Boolean = true

        internal fun shouldShowBlockedOverlay(
            hasShownBlockedOverlay: Boolean,
            nowMs: Long,
            suppressBlockedOverlayUntilMs: Long,
        ): Boolean {
            return !hasShownBlockedOverlay && nowMs >= suppressBlockedOverlayUntilMs
        }
    }
}
