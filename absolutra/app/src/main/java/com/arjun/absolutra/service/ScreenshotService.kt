package com.arjun.absolutra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.arjun.absolutra.data.BUTTON_OPACITY_KEY
import com.arjun.absolutra.data.BUTTON_SHAPE_ROUNDED_KEY
import com.arjun.absolutra.data.BUTTON_SIZE_KEY
import com.arjun.absolutra.data.dataStore
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenshotService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var selectionView: SelectionView? = null
    private var prefsJob: Job? = null
    private var currentIdleAlpha = 0.5f
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureThread = HandlerThread("absolutra-capture").apply { start() }
    private val captureHandler = Handler(captureThread.looper)

    companion object {
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, notification)
        }
        showFloatingView()
        return START_NOT_STICKY
    }

    private fun showFloatingView() {
        if (floatingView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        val icon = ImageView(this@ScreenshotService).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
        }

        val frame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#6650a4"))
            }
            val paddingDp = 12
            setPadding(paddingDp.dpToPx(), paddingDp.dpToPx(), paddingDp.dpToPx(), paddingDp.dpToPx())
            elevation = 8f
            addView(icon)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        frame.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    view.alpha = 1.0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = event.rawX - initialTouchX
                    val diffY = event.rawY - initialTouchY

                    if (abs(diffX) > 10 || abs(diffY) > 10) isDragging = true

                    if (isDragging) {
                        params.x = initialX + diffX.toInt()
                        params.y = initialY + diffY.toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.alpha = currentIdleAlpha

                    if (!isDragging) {
                        view.performClick()
                    } else {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val middle = screenWidth / 2
                        val buttonWidth = view.width.takeIf { it > 0 } ?: 1
                        params.x = if (params.x + (buttonWidth / 2) < middle) {
                            0
                        } else {
                            screenWidth - buttonWidth
                        }
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                else -> false
            }
        }

        frame.setOnClickListener {
            floatingView?.visibility = View.GONE
            showSelectionView()
        }

        floatingView = frame
        windowManager.addView(frame, params)

        prefsJob?.cancel()
        prefsJob = CoroutineScope(Dispatchers.IO).launch {
            dataStore.data.collect { prefs ->
                val size = prefs[BUTTON_SIZE_KEY] ?: 56
                currentIdleAlpha = prefs[BUTTON_OPACITY_KEY] ?: 0.5f
                val isRounded = prefs[BUTTON_SHAPE_ROUNDED_KEY] ?: true

                withContext(Dispatchers.Main) {
                    if (!isDragging) {
                        frame.alpha = currentIdleAlpha
                    }

                    (frame.background as? GradientDrawable)?.apply {
                        shape = if (isRounded) GradientDrawable.RECTANGLE else GradientDrawable.OVAL
                        cornerRadius = if (isRounded) 16f.dpToPx() else 0f
                    }

                    icon.layoutParams = FrameLayout.LayoutParams(size.dpToPx(), size.dpToPx()).apply {
                        gravity = Gravity.CENTER
                    }
                    frame.requestLayout()
                    windowManager.updateViewLayout(frame, params)
                }
            }
        }
    }

    private fun showSelectionView() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        @Suppress("DEPRECATION")
        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        selectionView = SelectionView(
            context = this,
            onCancel = {
                windowManager.removeView(selectionView)
                selectionView = null
                floatingView?.visibility = View.VISIBLE
            },
            onSelectionComplete = { top, bottom ->
                val viewHeight = selectionView?.height ?: 0
                windowManager.removeView(selectionView)
                selectionView = null

                mainHandler.postDelayed({
                    takeScreenshot(top, bottom, viewHeight)
                }, 200)
            }
        )

        windowManager.addView(selectionView, overlayParams)
    }

    private fun takeScreenshot(startY: Int, endY: Int, viewHeight: Int) {
        val accessibility = AbsolutraAccessibilityService.instance
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || accessibility == null) {
            floatingView?.visibility = View.VISIBLE
            Toast.makeText(this, "Enable Absolutra in Accessibility settings", Toast.LENGTH_SHORT).show()
            return
        }

        accessibility.captureScreen { bitmap ->
            if (bitmap == null) {
                floatingView?.visibility = View.VISIBLE
                Toast.makeText(this, "Screenshot failed", Toast.LENGTH_SHORT).show()
                return@captureScreen
            }
            captureHandler.post {
                var saved = false
                try {
                    val scale = if (viewHeight > 0) bitmap.height.toFloat() / viewHeight else 1f
                    val top = (startY * scale).toInt().coerceIn(0, bitmap.height - 1)
                    val bottom = (endY * scale).toInt().coerceIn(top + 1, bitmap.height)
                    val cropped = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bottom - top)
                    saveBitmap(cropped)
                    saved = true
                    if (cropped != bitmap) cropped.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    bitmap.recycle()
                    mainHandler.post {
                        floatingView?.visibility = View.VISIBLE
                        Toast.makeText(
                            this@ScreenshotService,
                            if (saved) "Screenshot saved!" else "Screenshot failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Absolutra")
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        prefsJob?.cancel()
        floatingView?.let { runCatching { windowManager.removeView(it) } }
        selectionView?.let { runCatching { windowManager.removeView(it) } }
        captureThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "screenshot_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screenshot Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Smart Screenshot Active")
            .setContentText("Floating button is waiting.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun Float.dpToPx(): Float = this * resources.displayMetrics.density
}
