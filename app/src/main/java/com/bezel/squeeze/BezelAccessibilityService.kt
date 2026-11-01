package com.bezel.squeeze

import android.animation.ValueAnimator
import android.accessibilityservice.AccessibilityService
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
import android.view.animation.PathInterpolator

class BezelAccessibilityService : AccessibilityService() {

    companion object {
        var isCapturingExtraKey = false
        var onKeyCapturedCallback: ((Int) -> Unit)? = null
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: BezelBulgeView

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.bezel.squeeze.UPDATE_SETTINGS" -> overlayView.loadSettings()
                "com.bezel.squeeze.TRIGGER_TEST" -> overlayView.onButtonPress(isVolumeUp = true, isTest = true)
                "com.bezel.squeeze.RELEASE_TEST" -> overlayView.onButtonRelease()
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
            addAction("com.bezel.squeeze.TRIGGER_TEST")
            addAction("com.bezel.squeeze.RELEASE_TEST")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsReceiver, filter)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Dynamic Key Learn Mode
        if (isCapturingExtraKey && event.action == KeyEvent.ACTION_DOWN) {
            onKeyCapturedCallback?.invoke(event.keyCode)
            isCapturingExtraKey = false
            return false
        }

        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val extraKey1 = prefs.getInt("extra_key_code_1", -1)
        val extraKey2 = prefs.getInt("extra_key_code_2", -1)

        val isVolUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        val isExtra = event.keyCode == extraKey1 || event.keyCode == extraKey2

        if (!isVolUp && !isVolDown && !isExtra) {
            return false
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            overlayView.onButtonPress(isVolumeUp = isVolUp, isExtra = isExtra)
        } else if (event.action == KeyEvent.ACTION_UP) {
            overlayView.onButtonRelease()
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
    private var activeLength = 340f
    private var isVolUpPressed = true
    private var isExtraPressed = false

    private var isRightEdge = true
    private var shapeProfile = 0
    private var unifiedRocker = true
    private var baseBulgeDepth = 42f
    private var pressDurationMs = 90L
    private var releaseDurationMs = 180L
    private var bulgeColor = Color.BLACK

    private var customScale = 1.0f
    private var customRotation = 0f

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val customStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    // Authentic iOS easing curve
    private val iosEaseOut = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)
    private val iosEaseIn = PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f)
    private var activeAnimator: ValueAnimator? = null

    init {
        loadSettings()
    }

    fun loadSettings() {
        isRightEdge = prefs.getBoolean("is_right_side", true)
        unifiedRocker = prefs.getBoolean("unified_rocker", true)
        shapeProfile = prefs.getInt("shape_profile", 0)
        baseBulgeDepth = prefs.getFloat("bulge_depth", 42f)
        pressDurationMs = prefs.getFloat("press_speed_ms", 90f).toLong().coerceIn(20L, 500L)
        releaseDurationMs = prefs.getFloat("release_speed_ms", 180f).toLong().coerceIn(40L, 800L)
        bulgeColor = prefs.getInt("bulge_color", Color.BLACK)
        customScale = prefs.getFloat("custom_shape_scale", 1.0f)
        customRotation = prefs.getFloat("custom_shape_rotation", 0f)

        paint.color = bulgeColor
        customStrokePaint.color = prefs.getInt("custom_stroke_color", Color.WHITE)
        invalidate()
    }

    fun onButtonPress(isVolumeUp: Boolean, isExtra: Boolean = false, isTest: Boolean = false) {
        loadSettings()
        isVolUpPressed = isVolumeUp
        isExtraPressed = isExtra

        val volUpY = prefs.getFloat("vol_up_y", 720f)
        val volDownY = prefs.getFloat("vol_down_y", 960f)
        val extraY = prefs.getFloat("extra_btn_y", 500f)

        if (isExtra) {
            activeCenterY = extraY
            activeLength = prefs.getFloat("extra_btn_length", 160f)
        } else if (unifiedRocker) {
            activeCenterY = (volUpY + volDownY) / 2f
            activeLength = prefs.getFloat("rocker_total_length", 380f)
        } else {
            activeCenterY = if (isVolumeUp) volUpY else volDownY
            activeLength = prefs.getFloat("single_btn_length", 160f)
        }

        activeAnimator?.cancel()
        activeAnimator = ValueAnimator.ofFloat(currentDepth, baseBulgeDepth).apply {
            duration = pressDurationMs
            interpolator = iosEaseOut
            addUpdateListener { currentDepth = it.animatedValue as Float }
            start()
        }
    }

    fun onButtonRelease() {
        activeAnimator?.cancel()
        activeAnimator = ValueAnimator.ofFloat(currentDepth, 0f).apply {
            duration = releaseDurationMs
            interpolator = iosEaseIn
            addUpdateListener { currentDepth = it.animatedValue as Float }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentDepth <= 0.1f) return

        path.reset()
        val totalLength = activeLength
        val startY = activeCenterY - (totalLength / 2f)
        val endY = activeCenterY + (totalLength / 2f)

        val edgeX = if (isRightEdge) width.toFloat() else 0f
        val peakX = if (isRightEdge) width - currentDepth else currentDepth

        if (shapeProfile == 4) { // Custom Shape Mode
            drawCustomRenderedShape(canvas, edgeX, activeCenterY)
            return
        }

        if (unifiedRocker && !isExtraPressed) {
            drawKinematicUnifiedRocker(edgeX, peakX, startY, endY, isVolUpPressed)
        } else {
            when (shapeProfile) {
                0 -> drawIosSquircle(edgeX, peakX, startY, endY)
                1 -> drawOrganicTeardrop(edgeX, peakX, startY, endY)
                2 -> drawGeometricPill(edgeX, peakX, startY, endY)
                3 -> drawBeveledWedge(edgeX, peakX, startY, endY)
            }
        }

        canvas.drawPath(path, paint)
    }

    /**
     * Kinematic unified rocker: Anchors the stationary pivot end to the bezel edge 
     * while swelling the pressed summit with dynamic curvature matching the physical toggle.
     */
    private fun drawKinematicUnifiedRocker(
        edgeX: Float,
        peakX: Float,
        startY: Float,
        endY: Float,
        isUp: Boolean
    ) {
        val rockerH = endY - startY
        val pressedApexY = if (isUp) startY + (rockerH * 0.22f) else endY - (rockerH * 0.22f)
        val passiveApexY = if (isUp) endY - (rockerH * 0.22f) else startY + (rockerH * 0.22f)

        val highX = peakX
        val lowX = edgeX + ((peakX - edgeX) * 0.26f)

        val topApexX = if (isUp) highX else lowX
        val botApexX = if (isUp) lowX else highX

        path.moveTo(edgeX, startY)
        // Top crest entry
        path.cubicTo(
            edgeX, startY + (rockerH * 0.08f),
            topApexX, pressedApexY - (rockerH * 0.08f),
            topApexX, pressedApexY
        )
        // Fluid rocker bridge saddle
        val saddleX = edgeX + ((peakX - edgeX) * 0.18f)
        val midY = (startY + endY) / 2f
        path.cubicTo(
            topApexX, midY - (rockerH * 0.12f),
            saddleX, midY,
            botApexX, passiveApexY - (rockerH * 0.12f)
        )
        // Bottom crest exit
        path.cubicTo(
            botApexX, passiveApexY + (rockerH * 0.08f),
            edgeX, endY - (rockerH * 0.08f),
            edgeX, endY
        )
        path.close()
    }

    private fun drawIosSquircle(edgeX: Float, peakX: Float, startY: Float, endY: Float) {
        val h = endY - startY
        val flatHalf = h * 0.16f
        val midY = (startY + endY) / 2f
        val flatTop = midY - flatHalf
        val flatBot = midY + flatHalf

        path.moveTo(edgeX, startY)
        path.cubicTo(edgeX, startY + (h * 0.22f), peakX, flatTop - (h * 0.08f), peakX, flatTop)
        path.lineTo(peakX, flatBot)
        path.cubicTo(peakX, flatBot + (h * 0.08f), edgeX, endY - (h * 0.22f), edgeX, endY)
        path.close()
    }

    private fun drawOrganicTeardrop(edgeX: Float, peakX: Float, startY: Float, endY: Float) {
        val midY = (startY + endY) / 2f
        val h = endY - startY
        path.moveTo(edgeX, startY)
        path.cubicTo(edgeX, startY + (h * 0.38f), peakX, midY - (h * 0.2f), peakX, midY)
        path.cubicTo(peakX, midY + (h * 0.2f), edgeX, endY - (h * 0.38f), edgeX, endY)
        path.close()
    }

    private fun drawGeometricPill(edgeX: Float, peakX: Float, startY: Float, endY: Float) {
        val midY = (startY + endY) / 2f
        path.moveTo(edgeX, startY)
        path.quadTo(peakX, (startY + midY) / 2f, peakX, midY)
        path.quadTo(peakX, (endY + midY) / 2f, edgeX, endY)
        path.close()
    }

    private fun drawBeveledWedge(edgeX: Float, peakX: Float, startY: Float, endY: Float) {
        val midY = (startY + endY) / 2f
        val plateau = (endY - startY) * 0.12f
        path.moveTo(edgeX, startY)
        path.lineTo(peakX, midY - plateau)
        path.lineTo(peakX, midY + plateau)
        path.lineTo(edgeX, endY)
        path.close()
    }

    private fun drawCustomRenderedShape(canvas: Canvas, edgeX: Float, centerY: Float) {
        val customShapeType = prefs.getInt("custom_geometric_type", 0) // 0 = Circle, 1 = Rounded Squircle, 2 = Star/Badge
        val shapeSize = (activeLength * 0.7f * customScale)
        val progress = (currentDepth / baseBulgeDepth.coerceAtLeast(1f))
        val currentBulgeOffset = progress * shapeSize * 0.85f

        val posX = if (isRightEdge) edgeX - currentBulgeOffset else edgeX + currentBulgeOffset
        val posY = centerY

        canvas.save()
        canvas.translate(posX, posY)
        canvas.rotate(if (isRightEdge) -customRotation else customRotation)

        val half = shapeSize / 2f
        val rect = RectF(-half, -half, half, half)

        when (customShapeType) {
            0 -> {
                canvas.drawCircle(0f, 0f, half, paint)
                canvas.drawCircle(0f, 0f, half, customStrokePaint)
            }
            1 -> {
                canvas.drawRoundRect(rect, 36f, 36f, paint)
                canvas.drawRoundRect(rect, 36f, 36f, customStrokePaint)
            }
            2 -> {
                val starPath = Path().apply {
                    val rOuter = half
                    val rInner = half * 0.5f
                    for (i in 0 until 10) {
                        val angle = (i * Math.PI / 5).toFloat()
                        val r = if (i % 2 == 0) rOuter else rInner
                        val x = r * kotlin.math.cos(angle)
                        val y = r * kotlin.math.sin(angle)
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                canvas.drawPath(starPath, paint)
                canvas.drawPath(starPath, customStrokePaint)
            }
        }
        canvas.restore()
    }
}
