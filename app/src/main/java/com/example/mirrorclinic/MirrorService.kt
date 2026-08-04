package com.example.mirrorclinic

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.IntentCompat
import kotlin.math.abs

/**
 * Freeze-and-mirror service.
 *
 * A floating "Mirror" button floats over every app. Tapping it captures ONE
 * frame of the current screen via MediaProjection, then shows that frozen frame
 * flipped horizontally in a full-screen overlay. Capturing a single frame (and
 * then stopping) avoids the feedback loop, which is what makes this work without
 * root.
 *
 * The button position is configurable (top / bottom / left / right, default top)
 * and can also be dragged anywhere with a finger.
 */
class MirrorService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "com.example.mirrorclinic.STOP"
        const val ACTION_SET_POSITION = "com.example.mirrorclinic.SET_POSITION"

        const val PREFS = "mirror_prefs"
        const val KEY_POS = "pos"       // "top" | "bottom" | "left" | "right"
        const val KEY_CUSTOM = "custom" // true if the user dragged it
        const val KEY_CX = "cx"
        const val KEY_CY = "cy"

        private const val CHANNEL_ID = "mirror_channel"
        private const val NOTIF_ID = 42
        private const val MARGIN = 24
    }

    private lateinit var windowManager: WindowManager
    private var mediaProjection: MediaProjection? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var mirrorView: View? = null
    private var mirrored = true

    private val captureThread = HandlerThread("capture").apply { start() }
    private val captureHandler = Handler(captureThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_SET_POSITION -> {
                if (mediaProjection == null) {
                    // Mirror isn't active; nothing to reposition.
                    stopSelf()
                    return START_NOT_STICKY
                }
                repositionBubble()
                return START_STICKY
            }
        }

        startForegroundNotification()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data = intent?.let {
            IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java)
        }

        if (mediaProjection == null && resultCode == Activity.RESULT_OK && data != null) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, data).also { mp ->
                mp.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { stopEverything() }
                }, mainHandler)
            }
            showBubble()
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen Mirror", NotificationManager.IMPORTANCE_LOW)
        )

        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, MirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Mirror is on")
            .setContentText("Tap the Mirror button to freeze and flip the screen.")
            .setSmallIcon(R.drawable.ic_launcher)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Stop", stopPi
                ).build()
            )
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun overlayParams(width: Int, height: Int, focusable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!focusable) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun showBubble() {
        if (bubbleView != null) return
        val btn = Button(this).apply { text = "Mirror" }
        val params = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            focusable = false
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        attachDrag(btn, params)
        windowManager.addView(btn, params)
        bubbleView = btn
        bubbleParams = params
        // Position once the view has been measured so we know its real size.
        btn.post {
            applyPosition(btn, params)
            runCatching { windowManager.updateViewLayout(btn, params) }
        }
    }

    private fun repositionBubble() {
        val v = bubbleView ?: return
        val p = bubbleParams ?: return
        applyPosition(v, p)
        runCatching { windowManager.updateViewLayout(v, p) }
    }

    private fun applyPosition(view: View, params: WindowManager.LayoutParams) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val spec = screenSpec()
        val w = if (view.width > 0) view.width else 200
        val h = if (view.height > 0) view.height else 130
        val maxX = (spec.width - w).coerceAtLeast(0)
        val maxY = (spec.height - h).coerceAtLeast(0)

        if (prefs.getBoolean(KEY_CUSTOM, false)) {
            params.x = prefs.getInt(KEY_CX, maxX).coerceIn(0, maxX)
            params.y = prefs.getInt(KEY_CY, MARGIN).coerceIn(0, maxY)
            return
        }

        when (prefs.getString(KEY_POS, "top")) {
            "bottom" -> { params.x = spec.width - w - MARGIN; params.y = spec.height - h - MARGIN }
            "left"   -> { params.x = MARGIN;                  params.y = (spec.height - h) / 2 }
            "right"  -> { params.x = spec.width - w - MARGIN; params.y = (spec.height - h) / 2 }
            else     -> { params.x = spec.width - w - MARGIN; params.y = MARGIN } // top (right corner)
        }
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    @Suppress("ClickableViewAccessibility")
    private fun attachDrag(view: View, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    if (moved) {
                        val spec = screenSpec()
                        params.x = (startX + dx).coerceIn(0, (spec.width - v.width).coerceAtLeast(0))
                        params.y = (startY + dy).coerceIn(0, (spec.height - v.height).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(v, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        captureAndShow()
                    } else {
                        // Remember the dragged spot.
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putBoolean(KEY_CUSTOM, true)
                            .putInt(KEY_CX, params.x)
                            .putInt(KEY_CY, params.y)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun hideBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        bubbleParams = null
    }

    private fun captureAndShow() {
        val mp = mediaProjection ?: return
        hideBubble()

        val spec = screenSpec()
        val width = spec.width
        val height = spec.height
        val density = spec.density

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                val bmp = imageToBitmap(image, width, height)
                image.close()
                reader.setOnImageAvailableListener(null, null)
                virtualDisplay?.release()
                reader.close()
                mainHandler.post { showMirror(bmp) }
            }
        }, captureHandler)

        // Small delay so the just-removed bubble is gone before we grab the frame.
        mainHandler.postDelayed({
            virtualDisplay = mp.createVirtualDisplay(
                "mirror-capture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, captureHandler
            )
        }, 150)
    }

    private data class ScreenSpec(val width: Int, val height: Int, val density: Int)

    private fun screenSpec(): ScreenSpec {
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            ScreenSpec(bounds.width(), bounds.height(), resources.configuration.densityDpi)
        } else {
            val dm = resources.displayMetrics
            ScreenSpec(dm.widthPixels, dm.heightPixels, dm.densityDpi)
        }
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val full = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        full.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) full else Bitmap.createBitmap(full, 0, 0, width, height)
    }

    private fun showMirror(bitmap: Bitmap) {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bitmap)
            scaleX = if (mirrored) -1f else 1f
        }
        root.addView(
            imageView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC000000.toInt())
        }
        val toggle = Button(this).apply {
            text = "Mirror: ON"
            setOnClickListener {
                mirrored = !mirrored
                imageView.scaleX = if (mirrored) -1f else 1f
                text = if (mirrored) "Mirror: ON" else "Mirror: OFF"
            }
        }
        val close = Button(this).apply {
            text = "Close"
            setOnClickListener {
                removeMirror()
                showBubble()
            }
        }
        bar.addView(toggle)
        bar.addView(close)
        root.addView(
            bar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )

        val params = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            focusable = true
        )
        windowManager.addView(root, params)
        mirrorView = root
    }

    private fun removeMirror() {
        mirrorView?.let { runCatching { windowManager.removeView(it) } }
        mirrorView = null
    }

    private fun stopEverything() {
        removeMirror()
        hideBubble()
        mediaProjection?.stop()
        mediaProjection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        captureThread.quitSafely()
    }
}
