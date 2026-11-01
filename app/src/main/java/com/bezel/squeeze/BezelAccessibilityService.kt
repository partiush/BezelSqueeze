package com.bezel.squeeze

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator

class BezelAccessibilityService : AccessibilityService() {

    companion object {
        var isListeningForKey = false
        var onKeyDetected: ((Int) -> Unit)? = null
    }

    private lateinit var windowManager: WindowManager
    private lateinit var bezelOverlay: BezelBulgeView
    private lateinit var volumePanelOverlay: CustomVolumePanelView
    private lateinit var audioManager: AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.bezel.squeeze.UPDATE_SETTINGS" -> {
                    bezelOverlay.loadSettings()
                    volumePanelOverlay.loadSettings()
                }
                "com.bezel.squeeze.TEST_PRESS" -> bezelOverlay.triggerPress(isVolumeUp = true)
                "com.bezel.squeeze.TEST_RELEASE" -> bezelOverlay.triggerRelease()
                "com.bezel.squeeze.TEST_PANEL" -> volumePanelOverlay.showPanel()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        bezelOverlay = BezelBulgeView(this)
        volumePanelOverlay = CustomVolumePanelView(this, audioManager)

        // Add Bezel Bulge Layer
        val bezelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(bezelOverlay, bezelParams)

        // Add Volume Panel Layer (Touchable)
        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(volumePanelOverlay, panelParams)

        val filter = IntentFilter().apply {
            addAction("com.bezel.squeeze.UPDATE_SETTINGS")
            addAction("com.bezel.squeeze.TEST_PRESS")
            addAction("com.bezel.squeeze.TEST_RELEASE")
            addAction("com.bezel.squeeze.TEST_PANEL")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsReceiver, filter)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isListeningForKey && event.action == KeyEvent.ACTION_DOWN) {
            onKeyDetected?.invoke(event.keyCode)
            isListeningForKey = false
            return false
        }

        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val extraKey = prefs.getInt("extra_button_code", -1)

        val isVolUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        val isExtra = event.keyCode == extraKey

        if (!isVolUp && !isVolDown && !isExtra) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            bezelOverlay.triggerPress(isVolumeUp = isVolUp, isExtra = isExtra)

            if (isVolUp || isVolDown) {
                // Adjust hardware audio stream
                val direction = if (isVolUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                mainHandler.post { volumePanelOverlay.showPanel() }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            bezelOverlay.triggerRelease()
        }

        // Return true on volume keys to suppress stock OS volume UI
        return isVolUp || isVolDown
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(settingsReceiver) } catch (_: Exception) {}
        if (::bezelOverlay.isInitialized) windowManager.removeView(bezelOverlay)
        if (::volumePanelOverlay.isInitialized) windowManager.removeView(volumePanelOverlay)
    }
}

class BezelBulgeView(context: Context) : View(context) {

    private val prefs = context.getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)

    var currentDepth = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var activeCenterY = 800f
    private var activeLength = 380f
    private var isRightEdge = true
    private var isUnifiedRocker = true
    private var isVolumeUpActive = true
    private var isExtraButtonActive = false

    private var shapeType = 0
    private var targetDepth = 42f
    private var bulgeColor = Color.BLACK

    private var animator: ValueAnimator? = null
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        loadSettings()
    }

    fun loadSettings() {
        isRightEdge = prefs.getBoolean("is_right_edge", true)
        isUnifiedRocker = prefs.getBoolean("is_unified_rocker", true)
        shapeType = prefs.getInt("shape_type", 0)
        targetDepth = prefs.getFloat("bulge_depth", 42f)
        bulgeColor = prefs.getInt("bulge_color", Color.BLACK)
        paint.color = bulgeColor
        invalidate()
    }

    fun triggerPress(isVolumeUp: Boolean, isExtra: Boolean = false) {
        loadSettings()
        isVolumeUpActive = isVolumeUp
        isExtraButtonActive = isExtra

        val volUpY = prefs.getFloat("vol_up_y", 720f)
        val volDownY = prefs.getFloat("vol_down_y", 960f)
        val extraY = prefs.getFloat("extra_y", 500f)

        if (isExtra) {
            activeCenterY = extraY
            activeLength = prefs.getFloat("extra_length", 160f)
        } else if (isUnifiedRocker) {
            activeCenterY = (volUpY + volDownY) / 2f
            activeLength = prefs.getFloat("rocker_length", 380f)
        } else {
            activeCenterY = if (isVolumeUp) volUpY else volDownY
            activeLength = prefs.getFloat("single_btn_length", 160f)
        }

        val pressSpeed = prefs.getFloat("press_speed_ms", 75f).toLong().coerceIn(10L, 500L)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(currentDepth, targetDepth).apply {
            duration = pressSpeed
            interpolator = DecelerateInterpolator()
            addUpdateListener { currentDepth = it.animatedValue as Float }
            start()
        }
    }

    fun triggerRelease() {
        val releaseSpeed = prefs.getFloat("release_speed_ms", 180f).toLong().coerceIn(30L, 800L)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(currentDepth, 0f).apply {
            duration = releaseSpeed
            interpolator = DecelerateInterpolator()
            addUpdateListener { currentDepth = it.animatedValue as Float }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentDepth <= 0.1f) return

        path.reset()
        val edgeX = if (isRightEdge) width.toFloat() else 0f
        val peakX = if (isRightEdge) width - currentDepth else currentDepth

        val startY = activeCenterY - (activeLength / 2f)
        val endY = activeCenterY + (activeLength / 2f)

        val focusY = if (isUnifiedRocker && !isExtraButtonActive) {
            if (isVolumeUpActive) startY + (activeLength * 0.28f) else startY + (activeLength * 0.72f)
        } else {
            activeCenterY
        }

        when (shapeType) {
            0 -> drawSquircle(edgeX, peakX, startY, endY, focusY)
            1 -> drawTeardrop(edgeX, peakX, startY, endY, focusY)
            2 -> drawPill(edgeX, peakX, startY, endY, focusY)
            3 -> drawBevel(edgeX, peakX, startY, endY, focusY)
            4 -> drawCustomVector(edgeX, startY, endY)
        }

        canvas.drawPath(path, paint)
    }

    private fun drawSquircle(edgeX: Float, peakX: Float, topY: Float, botY: Float, focusY: Float) {
        val flatHalf = activeLength * 0.16f
        val flatTop = focusY - flatHalf
        val flatBot = focusY + flatHalf

        path.moveTo(edgeX, topY)
        path.cubicTo(edgeX, topY + (flatTop - topY) * 0.55f, peakX, flatTop - (flatTop - topY) * 0.18f, peakX, flatTop)
        path.lineTo(peakX, flatBot)
        path.cubicTo(peakX, flatBot + (botY - flatBot) * 0.18f, edgeX, botY - (botY - flatBot) * 0.55f, edgeX, botY)
        path.close()
    }

    private fun drawTeardrop(edgeX: Float, peakX: Float, topY: Float, botY: Float, focusY: Float) {
        path.moveTo(edgeX, topY)
        path.cubicTo(edgeX, topY + (focusY - topY) * 0.45f, peakX, focusY - (focusY - topY) * 0.3f, peakX, focusY)
        path.cubicTo(peakX, focusY + (botY - focusY) * 0.3f, edgeX, botY - (botY - focusY) * 0.45f, edgeX, botY)
        path.close()
    }

    private fun drawPill(edgeX: Float, peakX: Float, topY: Float, botY: Float, focusY: Float) {
        path.moveTo(edgeX, topY)
        path.quadTo(peakX, (topY + focusY) / 2f, peakX, focusY)
        path.quadTo(peakX, (botY + focusY) / 2f, edgeX, botY)
        path.close()
    }

    private fun drawBevel(edgeX: Float, peakX: Float, topY: Float, botY: Float, focusY: Float) {
        val plate = activeLength * 0.12f
        path.moveTo(edgeX, topY)
        path.lineTo(peakX, focusY - plate)
        path.lineTo(peakX, focusY + plate)
        path.lineTo(edgeX, botY)
        path.close()
    }

    private fun drawCustomVector(edgeX: Float, topY: Float, botY: Float) {
        val raw = prefs.getString("custom_points_store", null)
        if (raw.isNullOrEmpty()) {
            drawSquircle(edgeX, if (isRightEdge) width - currentDepth else currentDepth, topY, botY, (topY + botY) / 2f)
            return
        }

        val tokens = raw.split(";").filter { it.contains(",") }
        if (tokens.size < 2) return

        val heightSpan = botY - topY
        val sign = if (isRightEdge) -1f else 1f

        path.moveTo(edgeX, topY)
        for (token in tokens) {
            val parts = token.split(",")
            val ny = parts[0].toFloatOrNull() ?: continue
            val nx = parts[1].toFloatOrNull() ?: continue
            val px = edgeX + (sign * nx * currentDepth)
            val py = topY + (ny * heightSpan)
            path.lineTo(px, py)
        }
        path.lineTo(edgeX, botY)
        path.close()
    }
}

class CustomVolumePanelView(
    context: Context,
    private val audioManager: AudioManager
) : View(context) {

    private val prefs = context.getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
    private var isVisibleOnScreen = false
    private var panelAlpha = 0f
    private var volumeLevel = 0.5f

    private var styleType = 0 // 0=iOS, 1=OneUI, 2=HyperOS, 3=ColorOS, 4=Pixel
    private var isRightSide = true
    private var panelY = 800f
    private var panelWidth = 46f
    private var panelHeight = 210f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        strokeWidth = 3f
    }

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { fadeOut() }

    init {
        loadSettings()
    }

    fun loadSettings() {
        styleType = prefs.getInt("volume_panel_style", 0)
        isRightSide = prefs.getBoolean("is_right_edge", true)
        panelY = prefs.getFloat("vol_up_y", 720f) + 100f
        panelWidth = prefs.getFloat("panel_width", 48f)
        panelHeight = prefs.getFloat("panel_height", 220f)
    }

    fun showPanel() {
        loadSettings()
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volumeLevel = (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)

        hideHandler.removeCallbacks(hideRunnable)
        isVisibleOnScreen = true

        ValueAnimator.ofFloat(panelAlpha, 1f).apply {
            duration = 140
            addUpdateListener {
                panelAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        hideHandler.postDelayed(hideRunnable, 2400)
    }

    private fun fadeOut() {
        ValueAnimator.ofFloat(panelAlpha, 0f).apply {
            duration = 220
            addUpdateListener {
                panelAlpha = it.animatedValue as Float
                invalidate()
                if (panelAlpha <= 0.05f) isVisibleOnScreen = false
            }
            start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isVisibleOnScreen || panelAlpha < 0.2f) return false

        val margin = 28f
        val left = if (isRightSide) width - panelWidth - margin else margin
        val right = left + panelWidth
        val top = panelY - (panelHeight / 2f)
        val bottom = top + panelHeight

        if (event.x >= left - 40 && event.x <= right + 40 && event.y >= top - 20 && event.y <= bottom + 20) {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    hideHandler.removeCallbacks(hideRunnable)
                    val raw = 1f - ((event.y - top) / panelHeight)
                    volumeLevel = raw.coerceIn(0f, 1f)
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeLevel * max).toInt(), 0)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hideHandler.postDelayed(hideRunnable, 2000)
                    return true
                }
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isVisibleOnScreen || panelAlpha <= 0.01f) return

        val margin = 32f
        val left = if (isRightSide) width - panelWidth - margin else margin
        val top = panelY - (panelHeight / 2f)
        val right = left + panelWidth
        val bottom = top + panelHeight
        val cornerRadius = when (styleType) {
            0 -> 34f // iOS squircle pill
            1 -> 24f // OneUI rounded
            2 -> 42f // HyperOS capsule
            3 -> 18f // ColorOS sharp-round
            else -> 48f // Pixel thick pill
        }

        val baseAlpha = (panelAlpha * 255).toInt()

        // 1. Panel Background
        when (styleType) {
            0 -> bgPaint.color = Color.argb((0.45f * baseAlpha).toInt(), 40, 40, 42) // iOS Blur Base
            1 -> bgPaint.color = Color.argb((0.75f * baseAlpha).toInt(), 30, 30, 33) // OneUI Base
            2 -> bgPaint.color = Color.argb((0.65f * baseAlpha).toInt(), 28, 28, 30) // HyperOS
            3 -> bgPaint.color = Color.argb((0.80f * baseAlpha).toInt(), 24, 24, 26) // ColorOS
            else -> bgPaint.color = Color.argb((0.70f * baseAlpha).toInt(), 34, 34, 38) // Pixel
        }
        val bgRect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

        // 2. Panel Fill Track
        val fillHeight = panelHeight * volumeLevel
        val fillTop = bottom - fillHeight
        when (styleType) {
            0 -> fillPaint.color = Color.argb(baseAlpha, 255, 255, 255) // iOS Pure White
            1 -> fillPaint.color = Color.argb(baseAlpha, 68, 138, 255) // OneUI Accent Blue
            2 -> fillPaint.color = Color.argb(baseAlpha, 255, 255, 255) // HyperOS Clean White
            3 -> fillPaint.color = Color.argb(baseAlpha, 0, 195, 137) // ColorOS Emerald
            else -> fillPaint.color = Color.argb(baseAlpha, 168, 199, 250) // Pixel Pastel Blue
        }

        canvas.save()
        canvas.clipRect(left, fillTop, right, bottom)
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, fillPaint)
        canvas.restore()

        // 3. Audio Speaker Icon
        val iconY = bottom - 28f
        val iconX = left + (panelWidth / 2f)
        iconPaint.alpha = baseAlpha
        iconPaint.color = if (volumeLevel > 0.2f && styleType == 0) Color.BLACK else Color.WHITE
        canvas.drawCircle(iconX, iconY, 6f, iconPaint)
    }
}
