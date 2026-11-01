package com.ios.bezelsqueeze

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityEvent
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class BezelAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: BezelBulgeView
    private var powerStripView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isHolding = false

    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            overlayView.loadSettings()
            setupPowerStrip()
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
        setupPowerStrip()

        val filter = IntentFilter("com.ios.bezelsqueeze.UPDATE_SETTINGS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsReceiver, filter)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPowerStrip() {
        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val powerEnabled = prefs.getBoolean("enable_power_button", true)
        val isRight = prefs.getBoolean("power_is_right", true)
        val powerY = prefs.getFloat("power_y", 1200f)
        val powerLength = prefs.getFloat("power_length", 180f)

        if (powerStripView != null) {
            try { windowManager.removeView(powerStripView) } catch (_: Exception) {}
            powerStripView = null
        }

        if (!powerEnabled) return

        powerStripView = View(this).apply {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> overlayView.onPowerPress()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> overlayView.onButtonRelease()
                }
                false
            }
        }

        val stripWidth = (resources.displayMetrics.density * 24).toInt()
        val stripParams = WindowManager.LayoutParams(
            stripWidth,
            powerLength.toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.TOP
            y = (powerY - (powerLength / 2f)).toInt()
        }

        try {
            windowManager.addView(powerStripView, stripParams)
        } catch (_: Exception) {}
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        val isUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                isHolding = true
                overlayView.onButtonPress(isVolumeUp = isUp)
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            isHolding = false
            // Minimum dwell timer: Guarantees snappy full visual registration even on a 10ms quick-tap
            mainHandler.postDelayed({
                if (!isHolding) {
                    overlayView.onButtonRelease()
                }
            }, 60)
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(settingsReceiver) } catch (_: Exception) {}
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
        powerStripView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
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
    private var activePeakBias = 0.5f

    private var isRightEdge = true
    private var shapeProfile = 0
    private var unifiedRocker = true
    private var baseBulgeDepth = 40f
    private var springStiffness = 3200f
    private var springDamping = 0.75f

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
        baseBulgeDepth = prefs.getFloat("bulge_depth", 40f)
        springStiffness = prefs.getFloat("speed_stiffness", 3200f)
        springDamping = prefs.getFloat("bounce_damping", 0.75f)
    }

    fun onButtonPress(isVolumeUp: Boolean) {
        loadSettings()
        val volUpY = prefs.getFloat("vol_up_y", 720f)
        val volDownY = prefs.getFloat("vol_down_y", 920f)

        if (unifiedRocker) {
            activeCenterY = (volUpY + volDownY) / 2f
            activeLength = prefs.getFloat("rocker_total_length", 320f)
            activePeakBias = if (isVolumeUp) 0.32f else 0.68f
        } else {
            activeCenterY = if (isVolumeUp) volUpY else volDownY
            activeLength = prefs.getFloat("single_btn_length", 150f)
            activePeakBias = 0.5f
        }

        animateTo(baseBulgeDepth)
    }

    fun onPowerPress() {
        loadSettings()
        isRightEdge = prefs.getBoolean("power_is_right", true)
        activeCenterY = prefs.getFloat("power_y", 1200f)
        activeLength = prefs.getFloat("power_length", 180f)
        activePeakBias = 0.5f

        animateTo(baseBulgeDepth)
    }

    fun onButtonRelease() {
        animateTo(0f)
    }

    private fun animateTo(target: Float) {
        springAnim.cancel()
        springAnim.spring = SpringForce(target).apply {
            stiffness = springStiffness
            dampingRatio = if (target == 0f) springDamping else 0.85f
        }
        springAnim.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentDepth <= 0.2f) return

        path.reset()
        val totalLength = activeLength
        val startY = activeCenterY - (totalLength * activePeakBias)
        val endY = activeCenterY + (totalLength * (1f - activePeakBias))
        val peakY = activeCenterY - (totalLength * (0.5f - activePeakBias) * 0.65f)

        val edgeX = if (isRightEdge) width.toFloat() else 0f
        val peakX = if (isRightEdge) width - currentDepth else currentDepth

        when (shapeProfile) {
            0 -> drawIosSquircle(edgeX, peakX, startY, endY, peakY)
            1 -> drawOrganicCurve(edgeX, peakX, startY, endY, peakY)
            2 -> drawOvalCurve(edgeX, peakX, startY, endY, peakY)
            3 -> drawSharpBevel(edgeX, peakX, startY, endY, peakY)
            4 -> drawParabolicCrescent(edgeX, peakX, startY, endY, peakY)
            5 -> drawLiquidSlime(edgeX, peakX, startY, endY, peakY)
            6 -> drawCustomUserShape(edgeX, peakX, startY, endY)
        }

        canvas.drawPath(path, paint)
    }

    // 0: iOS 18 Squircle with low-curvature tangent blending & flat crown
    private fun drawIosSquircle(edgeX: Float, peakX: Float, topY: Float, botY: Float, peakY: Float) {
        val flatHalfWidth = activeLength * 0.16f
        val flatTopY = peakY - flatHalfWidth
        val flatBotY = peakY + flatHalfWidth

        path.moveTo(edgeX, topY)
        path.cubicTo(
            edgeX, topY + (flatTopY - topY) * 0.62f,
            peakX, flatTopY - (flatTopY - topY) * 0.22f,
            peakX, flatTopY
        )
        path.lineTo(peakX, flatBotY)
        path.cubicTo(
            peakX, flatBotY + (botY - flatBotY) * 0.22f,
            edgeX, botY - (botY - flatBotY) * 0.62f,
            edgeX, botY
        )
        path.close()
    }

    // 1: Organic Natural Teardrop
    private fun drawOrganicCurve(edgeX: Float, peakX: Float, topY: Float, botY: Float, peakY: Float) {
        path.moveTo(edgeX, topY)
        path.cubicTo(
            edgeX, topY + (peakY - topY) * 0.5f,
            peakX, peakY - (peakY - topY) * 0.35f,
            peakX, peakY
        )
        path.cubicTo(
            peakX, peakY + (botY - peakY) * 0.35f,
            edgeX, botY - (botY - peakY) * 0.5f,
            edgeX, botY
        )
        path.close()
    }

    // 2: Oval / Pill
    private fun drawOvalCurve(edgeX: Float, peakX: Float, topY: Float, botY: Float, peakY: Float) {
        path.moveTo(edgeX, topY)
        path.quadTo(peakX, (topY + peakY) / 2f, peakX, peakY)
        path.quadTo(peakX, (botY + peakY) / 2f, edgeX, botY)
        path.close()
    }

    // 3: Sharp Bevel
    private fun drawSharpBevel(edgeX: Float, peakX: Float, topY: Float, botY: Float, peakY: Float) {
        path.moveTo(edgeX, topY)
        path.lineTo(peakX, peakY - (activeLength * 0.1f))
        path.lineTo(peakX, peakY + (activeLength * 0.1f))
        path.lineTo(edgeX, botY)
        path.close()
    }

    // 4: Parabolic Crescent
    private fun drawParabolicCrescent(edgeX: Float, peakX: Float, topY: Float, botY: Float, peakY: Float) {
        path.moveTo(edgeX, topY)
        path.cubicTo(peakX, topY, peakX, botY, edgeX, botY)
        path.close()
    }

    // 5: Liquid Slime (Bulbous inflection)
    private fun drawLiquidSlime(edgeX: Float, peakX: Float, topY: Float, botY: Float, peakY: Float) {
        val waistX = edgeX + (peakX - edgeX) * 0.15f
        path.moveTo(edgeX, topY)
        path.quadTo(waistX, topY + (peakY - topY) * 0.3f, peakX, peakY)
        path.quadTo(waistX, botY - (botY - peakY) * 0.3f, edgeX, botY)
        path.close()
    }

    // 6: Custom Shape from User-Drawn Points
    private fun drawCustomUserShape(edgeX: Float, peakX: Float, topY: Float, botY: Float) {
        val raw = prefs.getString("custom_points_data", null)
        if (raw.isNullOrEmpty()) {
            drawIosSquircle(edgeX, peakX, topY, botY, (topY + botY) / 2f)
            return
        }
        val pairs = raw.split(";").filter { it.contains(",") }
        if (pairs.size < 3) {
            drawIosSquircle(edgeX, peakX, topY, botY, (topY + botY) / 2f)
            return
        }

        path.moveTo(edgeX, topY)
        val h = botY - topY
        val depthSign = if (isRightEdge) -1f else 1f

        for (p in pairs) {
            val parts = p.split(",")
            val normY = parts[0].toFloatOrNull() ?: continue // 0.0 to 1.0
            val normDepth = parts[1].toFloatOrNull() ?: continue // 0.0 to 1.0
            val px = edgeX + (depthSign * normDepth * currentDepth)
            val py = topY + (normY * h)
            path.lineTo(px, py)
        }
        path.lineTo(edgeX, botY)
        path.close()
    }
}
