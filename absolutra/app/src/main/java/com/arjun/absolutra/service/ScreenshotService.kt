package com.arjun.absolutra.service

import android.app.Activity
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
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.arjun.absolutra.data.BUTTON_OPACITY_KEY
import com.arjun.absolutra.data.BUTTON_SHAPE_ROUNDED_KEY
import com.arjun.absolutra.data.BUTTON_SIZE_KEY
import com.arjun.absolutra.data.dataStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenshotService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var selectionView: SelectionView? = null
    private var durationView: View? = null
    private var prefsJob: Job? = null
    private var currentIdleAlpha = 0.5f
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureThread = HandlerThread("absolutra-capture").apply { start() }
    private val captureHandler = Handler(captureThread.looper)

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var activeTempFile: File? = null
    private var stopRecordingRunnable: Runnable? = null
    private var serviceDestroyed = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post { onProjectionRevoked() }
        }
    }

    companion object {
        private const val TAG = "ScreenshotService"
        private const val NOTIFICATION_ID = 2
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "START_RECORD_AREA" -> {
                val duration = intent.getIntExtra("duration", 5).coerceIn(1, 3600)
                val code = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
                val data = intent.captureResult()

                startCaptureForeground(includeProjection = true)
                showFloatingView()
                floatingView?.visibility = View.GONE

                if (mediaProjection == null) {
                    val granted = data != null && code == Activity.RESULT_OK && acquireProjection(code, data)
                    if (!granted) {
                        startCaptureForeground(includeProjection = false)
                        floatingView?.visibility = View.VISIBLE
                        Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                        return START_NOT_STICKY
                    }
                }
                showSelectionViewForRecording(duration)
                return START_NOT_STICKY
            }
            "CANCEL_RECORD" -> {
                dismissDurationInput()
                showFloatingView()
                floatingView?.visibility = View.VISIBLE
                return START_NOT_STICKY
            }
        }

        startCaptureForeground(includeProjection = mediaProjection != null)
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
            background = GradientDrawable().apply { setColor(Color.parseColor("#6650a4")) }
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
        var longPressFired = false

        val longPressRunnable = Runnable {
            if (!isDragging) {
                longPressFired = true
                showDurationInput()
            }
        }

        frame.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    longPressFired = false
                    view.alpha = 1.0f
                    mainHandler.postDelayed(longPressRunnable, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = event.rawX - initialTouchX
                    val diffY = event.rawY - initialTouchY

                    if (abs(diffX) > 10 || abs(diffY) > 10) {
                        isDragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                    }

                    if (isDragging && !longPressFired) {
                        params.x = initialX + diffX.toInt()
                        params.y = initialY + diffY.toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.alpha = currentIdleAlpha
                    mainHandler.removeCallbacks(longPressRunnable)

                    if (longPressFired) {
                        longPressFired = false
                    } else if (!isDragging) {
                        view.performClick()
                    } else {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val middle = screenWidth / 2
                        val buttonWidth = view.width.takeIf { it > 0 } ?: 1
                        params.x = if (params.x + (buttonWidth / 2) < middle) 0 else screenWidth - buttonWidth
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    view.alpha = currentIdleAlpha
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
                    if (!isDragging) frame.alpha = currentIdleAlpha
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

    private fun showDurationInput() {
        if (durationView != null) return
        floatingView?.visibility = View.GONE

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6FFFFFF"))
            val pad = 24.dpToPx()
            setPadding(pad, pad, pad, pad)
            elevation = 16f
        }
        val title = TextView(this).apply {
            text = "Record Duration (seconds)"
            setTextColor(Color.BLACK)
            textSize = 18f
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("5")
            setTextColor(Color.BLACK)
        }
        val matchWidth = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        input.layoutParams = matchWidth
        val startButton = Button(this).apply {
            text = "Select Area to Record"
            layoutParams = matchWidth
            setOnClickListener {
                val sec = input.text.toString().toIntOrNull()?.coerceIn(1, 3600) ?: 5
                dismissDurationInput()
                if (mediaProjection != null) {
                    startCaptureForeground(includeProjection = true)
                    showSelectionViewForRecording(sec)
                } else {
                    val permissionIntent = Intent(this@ScreenshotService, RecordPermissionActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("duration", sec)
                    }
                    try {
                        startActivity(permissionIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Couldn't open screen-record permission", e)
                        floatingView?.visibility = View.VISIBLE
                        Toast.makeText(
                            this@ScreenshotService,
                            "Couldn't open screen recording permission",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        val cancelButton = Button(this).apply {
            text = "Cancel"
            layoutParams = matchWidth
            setOnClickListener {
                dismissDurationInput()
                floatingView?.visibility = View.VISIBLE
            }
        }
        layout.addView(title)
        layout.addView(input)
        layout.addView(startButton)
        layout.addView(cancelButton)

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        }

        durationView = layout
        windowManager.addView(layout, overlayParams)
        input.requestFocus()
    }

    private fun dismissDurationInput() {
        durationView?.let { runCatching { windowManager.removeView(it) } }
        durationView = null
    }

    private fun showSelectionViewForRecording(duration: Int) {
        if (selectionView != null) return
        val overlayParams = fullScreenOverlayParams()

        selectionView = SelectionView(
            context = this,
            onCancel = {
                windowManager.removeView(selectionView)
                selectionView = null
                floatingView?.visibility = View.VISIBLE
            },
            onSelectionComplete = { top, bottom ->
                val overlayHeight = selectionView?.height ?: 1
                windowManager.removeView(selectionView)
                selectionView = null
                mainHandler.postDelayed({
                    startScreenRecording(top, bottom, overlayHeight, duration)
                }, 200)
            }
        )
        windowManager.addView(selectionView, overlayParams)
    }

    private fun startScreenRecording(top: Int, bottom: Int, overlayHeight: Int, duration: Int) {
        val projection = mediaProjection
        if (projection == null) {
            floatingView?.visibility = View.VISIBLE
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            return
        }

        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels - (metrics.widthPixels % 16)
        val screenHeight = metrics.heightPixels - (metrics.heightPixels % 16)

        val tempFile = File(cacheDir, "temp_record_${System.currentTimeMillis()}.mp4")
        activeTempFile = tempFile

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(screenWidth, screenHeight)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(5_000_000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        setVideoEncodingProfileLevel(
                            android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                            android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel4
                        )
                    } catch (profileError: Exception) {
                        Log.w(TAG, "H.264 High profile unavailable", profileError)
                    }
                }
                setOutputFile(tempFile.absolutePath)
                prepare()
            }

            val display = projection.createVirtualDisplay(
                "ScreenRecord",
                screenWidth,
                screenHeight,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            ) ?: error("Virtual display was not created")

            recorder.start()
            mediaRecorder = recorder
            virtualDisplay = display

            Toast.makeText(this, "Recording started for $duration sec...", Toast.LENGTH_SHORT).show()

            val stop = Runnable {
                stopScreenRecordingAndCrop(tempFile, top, bottom, overlayHeight, screenHeight)
            }
            stopRecordingRunnable = stop
            mainHandler.postDelayed(stop, duration * 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed to start. Size: ${screenWidth}x${screenHeight}", e)
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
            mediaRecorder = null
            runCatching { virtualDisplay?.release() }
            virtualDisplay = null
            tempFile.delete()
            activeTempFile = null
            floatingView?.visibility = View.VISIBLE
            Toast.makeText(this, "Recording setup failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopScreenRecordingAndCrop(
        tempFile: File,
        top: Int,
        bottom: Int,
        overlayHeight: Int,
        screenHeight: Int
    ) {
        stopRecordingRunnable = null
        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed to stop properly", e)
            runCatching { mediaRecorder?.release() }
            runCatching { virtualDisplay?.release() }
        }
        mediaRecorder = null
        virtualDisplay = null

        if (!tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            activeTempFile = null
            floatingView?.visibility = View.VISIBLE
            Toast.makeText(this, "Recording failed (Empty file)", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Cropping video with FFmpeg...", Toast.LENGTH_SHORT).show()
        cropAndSave(tempFile, top, bottom, overlayHeight, screenHeight)
    }

    private fun cropAndSave(
        tempFile: File,
        top: Int,
        bottom: Int,
        overlayHeight: Int,
        screenHeight: Int
    ) {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels - (metrics.widthPixels % 16)

        val scale = screenHeight.toFloat() / overlayHeight.coerceAtLeast(1)

        var cropTop = (minOf(top, bottom) * scale).roundToInt().coerceIn(0, screenHeight - 2)
        var cropBottom = (maxOf(top, bottom) * scale).roundToInt().coerceIn(cropTop + 2, screenHeight)

        cropTop = cropTop and 1.inv()
        cropBottom = cropBottom and 1.inv()

        val cropHeight = (cropBottom - cropTop) and 1.inv()

        if (cropHeight < 16) {
            Log.e(TAG, "Crop area too small. Saving full recording.")
            saveVideoToMediaStore(tempFile, isFallback = true)
            return
        }

        val finalFile = File(cacheDir, "final_cropped_${System.currentTimeMillis()}.mp4")
        val cropFilter = "crop=$screenWidth:$cropHeight:0:$cropTop"
        val command = "-y -i \"${tempFile.absolutePath}\" -vf \"$cropFilter\" -c:v libx264 -preset ultrafast -crf 23 -an \"${finalFile.absolutePath}\""

        FFmpegKit.executeAsync(command) { session ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                tempFile.delete()
                activeTempFile = null
                saveVideoToMediaStore(finalFile, isFallback = false)
            } else {
                Log.e(
                    TAG,
                    "FFmpeg failed with state ${session.state} and return code $returnCode.\n${session.failStackTrace}"
                )
                finalFile.delete()
                if (serviceDestroyed) {
                    tempFile.delete()
                } else {
                    activeTempFile = null
                    saveVideoToMediaStore(tempFile, isFallback = true)
                }
            }
        }
    }

    private fun saveVideoToMediaStore(videoFile: File, isFallback: Boolean) {
        captureHandler.post {
            val fileName = "Absolutra_Rec_${System.currentTimeMillis()}.mp4"
            var saved = false

            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Absolutra")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outStream ->
                        videoFile.inputStream().use { inStream -> inStream.copyTo(outStream) }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Video.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                    }
                    saved = true
                } else {
                    val directDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "Absolutra"
                    )
                    if (!directDir.exists()) directDir.mkdirs()
                    val directDest = File(directDir, fileName)
                    videoFile.copyTo(directDest, overwrite = true)
                    saved = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save final video", e)
            } finally {
                videoFile.delete()
            }

            if (serviceDestroyed) return@post

            val message = if (saved) {
                if (isFallback) "Video saved (full screen - crop failed)" else "Video successfully chopped!"
            } else {
                "Failed to save video to Gallery"
            }

            mainHandler.post {
                floatingView?.visibility = View.VISIBLE
                Toast.makeText(this@ScreenshotService, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun acquireProjection(resultCode: Int, data: Intent): Boolean {
        return try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val projection = manager.getMediaProjection(resultCode, data) ?: return false
            projection.registerCallback(projectionCallback, mainHandler)
            mediaProjection = projection
            true
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection grant failed", e)
            false
        }
    }

    private fun onProjectionRevoked() {
        mediaProjection = null
        if (serviceDestroyed) return
        if (mediaRecorder != null || virtualDisplay != null) {
            stopRecordingRunnable?.let { mainHandler.removeCallbacks(it) }
            stopRecordingRunnable = null
            runCatching { mediaRecorder?.stop() }
            runCatching { mediaRecorder?.reset() }
            runCatching { mediaRecorder?.release() }
            runCatching { virtualDisplay?.release() }
            mediaRecorder = null
            virtualDisplay = null
            activeTempFile?.delete()
            activeTempFile = null
            floatingView?.visibility = View.VISIBLE
            Toast.makeText(this, "Recording stopped by system", Toast.LENGTH_SHORT).show()
        }
        if (isRunning) startCaptureForeground(includeProjection = false)
    }

    private fun showSelectionView() {
        val overlayParams = fullScreenOverlayParams()

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

    private fun fullScreenOverlayParams(): WindowManager.LayoutParams {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        @Suppress("DEPRECATION")
        return WindowManager.LayoutParams(
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

    private fun startCaptureForeground(includeProjection: Boolean) {
        val notification = createNotification()
        val useProjection = includeProjection || mediaProjection != null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (useProjection) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else if (useProjection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun Intent.captureResult(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra("data")
        }
    }

    override fun onDestroy() {
        serviceDestroyed = true
        isRunning = false
        prefsJob?.cancel()
        stopRecordingRunnable?.let { mainHandler.removeCallbacks(it) }

        FFmpegKit.cancel()

        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.reset() }
        runCatching { mediaRecorder?.release() }
        runCatching { virtualDisplay?.release() }
        mediaRecorder = null
        virtualDisplay = null
        activeTempFile?.delete()
        activeTempFile = null
        mediaProjection?.let { projection ->
            runCatching { projection.unregisterCallback(projectionCallback) }
            runCatching { projection.stop() }
        }
        mediaProjection = null
        dismissDurationInput()
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
