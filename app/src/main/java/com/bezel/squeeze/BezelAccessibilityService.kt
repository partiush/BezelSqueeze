package com.bezel.squeeze

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator

class BezelAccessibilityService : AccessibilityService() {

    companion object {
        var isListeningForKey = false
        var onKeyDetected: ((Int) -> Unit)? = null
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: BezelBulgeView

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.bezel.squeeze.UPDATE_SETTINGS" -> overlayView.loadSettings()
                "com.bezel.squeeze.TEST_PRESS" -> overlayView.triggerPress(isVolumeUp = true)
                "com.bezel.squeeze.TEST_RELEASE" -> overlayView.triggerRelease()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = BezelBulgeView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(overlayView, params)

        val filter = IntentFilter().apply {
            addAction("com.bezel.squeeze.UPDATE_SETTINGS")
            addAction("com.bezel.squeeze.TEST_PRESS")
            addAction("com.bezel.squeeze.TEST_RELEASE")
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
            overlayView.triggerPress(isVolumeUp = isVolUp, isExtra = isExtra)
        } else if (event.action == KeyEvent.ACTION_UP) {
            overlayView.triggerRelease()
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(settingsReceiver) } catch (_: Exception) {}
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
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
    private var activeLength = 320f
    private var isRightEdge = true
    private var isUnifiedRocker = true
    private var isVolumeUpActive = true
    private var isExtraButtonActive = false

    private var shapeType = 0
    private var targetDepth = 40f
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
        targetDepth = prefs.getFloat("bulge_depth", 40f)
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

        val pressSpeed = prefs.getFloat("press_speed_ms", 85f).toLong().coerceIn(20L, 400L)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(currentDepth, targetDepth).apply {
            duration = pressSpeed
            interpolator = DecelerateInterpolator()
            addUpdateListener { currentDepth = it.animatedValue as Float }
            start()
        }
    }

    fun triggerRelease() {
        val releaseSpeed = prefs.getFloat("release_speed_ms", 160f).toLong().coerceIn(40L, 600L)
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
        if (currentDepth <= 0.2f) return

        path.reset()
        val edgeX = if (isRightEdge) width.toFloat() else 0f
        val peakX = if (isRightEdge) width - currentDepth else currentDepth

        val startY = activeCenterY - (activeLength / 2f)
        val endY = activeCenterY + (activeLength / 2f)

        val focusCenterY = if (isUnifiedRocker && !isExtraButtonActive) {
            if (isVolumeUpActive) startY + (activeLength * 0.32f) else startY + (activeLength * 0.68f)
        } else {
            activeCenterY
        }

        when (shapeType) {
            0 -> drawSquircle(edgeX, peakX, startY, endY, focusCenterY)
            1 -> drawTeardrop(edgeX, peakX, startY, endY, focusCenterY)
            2 -> drawPill(edgeX, peakX, startY, endY, focusCenterY)
            3 -> drawBevel(edgeX, peakX, startY, endY, focusCenterY)
            4 -> drawCustomUserContour(edgeX, startY, endY)
        }

        canvas.drawPath(path, paint)
    }

    private fun drawSquircle(edgeX: Float, peakX: Float, topY: Float, botY: Float, focusY: Float) {
        val flatHalf = activeLength * 0.14f
        val flatTop = focusY - flatHalf
        val flatBot = focusY + flatHalf

        path.moveTo(edgeX, topY)
        path.cubicTo(edgeX, topY + (flatTop - topY) * 0.6f, peakX, flatTop - (flatTop - topY) * 0.2f, peakX, flatTop)
        path.lineTo(peakX, flatBot)
        path.cubicTo(peakX, flatBot + (botY - flatBot) * 0.2f, edgeX, botY - (botY - flatBot) * 0.6f, edgeX, botY)
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
        val flatWidth = activeLength * 0.12f
        path.moveTo(edgeX, topY)
        path.lineTo(peakX, focusY - flatWidth)
        path.lineTo(peakX, focusY + flatWidth)
        path.lineTo(edgeX, botY)
        path.close()
    }

    private fun drawCustomUserContour(edgeX: Float, topY: Float, botY: Float) {
        val raw = prefs.getString("custom_drawing_points", null)
        if (raw.isNullOrEmpty()) {
            val peakX = if (isRightEdge) width - currentDepth else currentDepth
            drawSquircle(edgeX, peakX, topY, botY, (topY + botY) / 2f)
            return
        }

        val pairs = raw.split(";").filter { it.contains(",") }
        if (pairs.size < 2) {
            val peakX = if (isRightEdge) width - currentDepth else currentDepth
            drawSquircle(edgeX, peakX, topY, botY, (topY + botY) / 2f)
            return
        }

        val heightSpan = botY - topY
        val sign = if (isRightEdge) -1f else 1f

        path.moveTo(edgeX, topY)

        val firstPt = pairs.first().split(",")
        val firstY = topY + ((firstPt[0].toFloatOrNull() ?: 0f) * heightSpan)
        val firstX = edgeX + (sign * (firstPt[1].toFloatOrNull() ?: 0f) * currentDepth)
        path.lineTo(firstX, firstY)

        for (i in 1 until pairs.size) {
            val curr = pairs[i].split(",")
            val ny = curr[0].toFloatOrNull() ?: continue
            val nx = curr[1].toFloatOrNull() ?: continue
            val px = edgeX + (sign * nx * currentDepth)
            val py = topY + (ny * heightSpan)
            path.lineTo(px, py)
        }

        path.lineTo(edgeX, botY)
        path.close()
    }
}
