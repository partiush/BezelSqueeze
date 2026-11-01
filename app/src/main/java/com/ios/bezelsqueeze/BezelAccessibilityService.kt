package com.ios.bezelsqueeze

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.*
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
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val isRightSide = prefs.getBoolean("is_right_side", true)
        val maxDepth = prefs.getFloat("bulge_depth", 42f)
        val buttonLength = prefs.getFloat("button_length", 170f)
        val stiffness = prefs.getFloat("spring_stiffness", SpringForce.STIFFNESS_MEDIUM)

        val targetY = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> prefs.getFloat("vol_up_y", 750f)
            KeyEvent.KEYCODE_VOLUME_DOWN -> prefs.getFloat("vol_down_y", 960f)
            else -> return false
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            overlayView.triggerBulge(
                yCenter = targetY,
                length = buttonLength,
                targetDepth = maxDepth,
                onRightEdge = isRightSide,
                stiffness = stiffness
            )
        } else if (event.action == KeyEvent.ACTION_UP) {
            overlayView.releaseBulge()
        }
        // Return false so Android still changes the system volume normally
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
    }
}

class BezelBulgeView(context: Context) : View(context) {
    var currentDepth = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var centerY = 800f
    private var bulgeLength = 170f
    private var isRight = true
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

    private val springAnim = SpringAnimation(this, depthProperty, 0f).apply {
        spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        spring.stiffness = SpringForce.STIFFNESS_MEDIUM
    }

    fun triggerBulge(yCenter: Float, length: Float, targetDepth: Float, onRightEdge: Boolean, stiffness: Float) {
        centerY = yCenter
        bulgeLength = length
        isRight = onRightEdge
        springAnim.spring.stiffness = stiffness
        springAnim.animateToFinalPosition(targetDepth)
    }

    fun releaseBulge() {
        springAnim.animateToFinalPosition(0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentDepth <= 0.5f) return

        path.reset()
        val halfL = bulgeLength / 2f
        val startY = centerY - halfL
        val endY = centerY + halfL
        val edgeX = if (isRight) width.toFloat() else 0f
        val peakX = if (isRight) width - currentDepth else currentDepth

        // Smooth iOS-style S-curve Cubic Bezier bulge
        path.moveTo(edgeX, startY)
        path.cubicTo(
            edgeX, startY + halfL * 0.45f,
            peakX, centerY - halfL * 0.35f,
            peakX, centerY
        )
        path.cubicTo(
            peakX, centerY + halfL * 0.35f,
            edgeX, endY - halfL * 0.45f,
            edgeX, endY
        )
        path.close()
        canvas.drawPath(path, paint)
    }
}
