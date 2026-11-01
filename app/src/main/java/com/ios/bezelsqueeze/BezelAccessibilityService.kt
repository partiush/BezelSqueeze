package com.ios.bezelsqueeze

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
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class BezelAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: BezelBulgeView

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            overlayView.loadSettings()
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

        val filter = IntentFilter("com.ios.bezelsqueeze.UPDATE_SETTINGS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsReceiver, filter)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        val isUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            overlayView.onButtonPress(isVolumeUp = isUp)
        } else if (event.action == KeyEvent.ACTION_UP) {
            overlayView.onButtonRelease()
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(settingsReceiver)
        } catch (_: Exception) {}
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
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
    private var activeLength = 260f
    private var activePeakBias = 0.5f // 0.5 = centered; <0.5 = biased up; >0.5 = biased down

    private var isRightEdge = true
    private var shapeProfile = 0 // 0 = iOS 18 Squircle, 1 = Organic, 2 = Oval
    private var unifiedRocker = true
    private var baseBulgeDepth = 36f

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val depthProperty = object : FloatPropertyCompat<BezelBulgeView>("depth") {
        override fun getValue(view: BezelBulgeView): Float = view.currentDepth
        override fun setValue(view: BezelBulgeView, value: Float) {
            view.currentDepth = value
        }
    }

    private val springAnim = SpringAnimation(this, depthProperty, 0f)

    init {
        loadSettings()
    }

    fun loadSettings() {
        isRightEdge = prefs.getBoolean("is_right_side", true)
        unifiedRocker = prefs.getBoolean("unified_rocker", true)
        shapeProfile = prefs.getInt("shape_profile", 0)
        baseBulgeDepth = prefs.getFloat("bulge_depth", 36f)
    }

    fun onButtonPress(isVolumeUp: Boolean) {
        loadSettings()

        val volUpY = prefs.getFloat("vol_up_y", 720f)
        val volDownY = prefs.getFloat("vol_down_y", 920f)

        if (unifiedRocker) {
            // Span the entire rocker smoothly, tilt depth peak toward pressed side
            activeCenterY = (volUpY + volDownY) / 2f
            activeLength = prefs.getFloat("rocker_total_length", 320f)
            activePeakBias = if (isVolumeUp) 0.32f else 0.68f
        } else {
            // Split discrete buttons
            activeCenterY = if (isVolumeUp) volUpY else volDownY
            activeLength = prefs.getFloat("single_btn_length", 140f)
            activePeakBias = 0.5f
        }

        // Punchy, zero-latency down stroke
        springAnim.cancel()
        springAnim.spring = SpringForce(baseBulgeDepth).apply {
            stiffness = 2200f
            dampingRatio = 0.82f // Crisp impact without excessive wobble
        }
        springAnim.start()
    }

    fun onButtonRelease() {
        // High-frequency elastic snap-back
        springAnim.cancel()
        springAnim.spring = SpringForce(0f).apply {
            stiffness = 1800f
            dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        }
        springAnim.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentDepth <= 0.3f) return

        path.reset()
        val totalLength = activeLength
        val startY = activeCenterY - (totalLength * activePeakBias)
        val endY = activeCenterY + (totalLength * (1f - activePeakBias))
        val peakY = activeCenterY - (totalLength * (0.5f - activePeakBias) * 0.6f)

        val edgeX = if (isRightEdge) width.toFloat() else 0f
        val peakX = if (isRightEdge) width - currentDepth else currentDepth

        when (shapeProfile) {
            0 -> drawIosSquircle(edgeX, peakX, startY, endY, peakY)
            1 -> drawOrganicCurve(edgeX, peakX, startY, endY, peakY)
            2 -> drawOvalCurve(edgeX, peakX, startY, endY, peakY)
        }

        canvas.drawPath(path, paint)
    }

    /**
     * Replicates iOS 18 high-tension squircle: flat summit, tight tangent blending into frame
     */
    private fun drawIosSquircle(edgeX: Float, peakX: Float, topY: Float, botY: Float, peakY: Float) {
        val flatHalfWidth = activeLength * 0.16f
        val flatTopY = peakY - flatHalfWidth
        val flatBotY = peakY + flatHalfWidth

        path.moveTo(edgeX, topY)
        // Upper transition
        path.cubicTo(
            edgeX, topY + (flatTopY - topY) * 0.62f,
            peakX, flatTopY - (flatTopY - topY) * 0.22f,
            peakX, flatTopY
        )
        // Subtle flat contact lip against button
        path.lineTo(peakX, flatBotY)
        // Lower transition back to bezel
        path.cubicTo(
            peakX, flatBotY + (botY - flatBotY) * 0.22f,
            edgeX, botY - (botY - flatBotY) * 0.62f,
            edgeX, botY
        )
        path.close()
    }

    private fun drawOrganicCurve(edgeX: Float, peakX: Float, topY: Float, botY: Float, peakY: Float) {
        path.moveTo(edgeX, topY)
        path.cubicTo(
            edgeX, topY + (peakY - topY) * 0.45f,
            peakX, peakY - (peakY - topY) * 0.35f,
            peakX, peakY
        )
        path.cubicTo(
            peakX, peakY + (botY - peakY) * 0.35f,
            edgeX, botY - (botY - peakY) * 0.45f,
            edgeX, botY
        )
        path.close()
    }

    private fun drawOvalCurve(edgeX: Float, peakX: Float, topY: Float, botY: Float, peakY: Float) {
        val midTop = topY + (peakY - topY) * 0.5f
        val midBot = peakY + (botY - peakY) * 0.5f
        path.moveTo(edgeX, topY)
        path.quadTo(peakX, midTop, peakX, peakY)
        path.quadTo(peakX, midBot, edgeX, botY)
        path.close()
    }
}
