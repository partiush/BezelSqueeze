package com.bezel.squeeze

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
    private var isHoldingKey = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("enable_power_button", true)) {
                    overlayView.onPowerPress()
                    mainHandler.postDelayed({ overlayView.onButtonRelease() }, 200L)
                }
            }
        }
    }

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

        val filter = IntentFilter().apply {
            addAction("com.bezel.squeeze.UPDATE_SETTINGS")
            addAction("com.bezel.squeeze.TRIGGER_PREVIEW")
        }
        val screenFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(settingsReceiver, filter)
            registerReceiver(screenReceiver, screenFilter)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPowerStrip() {
        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val powerEnabled = prefs.getBoolean("enable_power_button", true)
        val isRight = prefs.getBoolean("power_is_right", true)
        val powerY = prefs.getFloat("power_y", 1200f)
        val powerLength = prefs.getFloat("power_length", 180f)

        powerStripView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
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

        val stripWidth = (resources.displayMetrics.density * 32).toInt()
        val stripParams = WindowManager.LayoutParams(
            stripWidth,
            powerLength.toInt().coerceAtLeast(40),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.TOP
            y = (powerY - (powerLength / 2f)).toInt().coerceAtLeast(0)
        }

        try {
            windowManager.addView(powerStripView, stripParams)
        } catch (_: Exception) {}
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val latencyMs = prefs.getFloat("input_latency_ms", 0f).toLong()
        val isUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                isHoldingKey = true
                if (latencyMs <= 0L) {
                    overlayView.onButtonPress(isUp)
                } else {
                    mainHandler.postDelayed({ if (isHoldingKey) overlayView.onButtonPress(isUp) }, latencyMs)
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            isHoldingKey = false
            val delay = 50L.coerceAtLeast(latencyMs)
            mainHandler.postDelayed({
                if (!isHoldingKey) overlayView.onButtonRelease()
            }, delay)
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(settingsReceiver)
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
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
    private var isVolumeUpActive = true

    private var isRightEdge = true
    private var shapeProfile = 0
    private var unifiedRocker = true
    private var baseBulgeDepth = 40f
    private var springStiffness = 3400f
    private var springDamping = 0.75f
    private var bulgeColor = Color.BLACK

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        springStiffness = prefs.getFloat("speed_stiffness", 3400f)
        springDamping = prefs.getFloat("bounce_damping", 0.75f)
        bulgeColor = prefs.getInt("bulge_color", Color.BLACK)
        paint.color = bulgeColor
        invalidate()
    }

    fun onButtonPress(isVolumeUp: Boolean) {
        loadSettings()
        isVolumeUpActive = isVolumeUp

        val volUpY = prefs.getFloat("vol_up_y", 720f)
        val volDownY = prefs.getFloat("vol_down_y", 960f)

        if (unifiedRocker) {
            activeCenterY = (volUpY + volDownY) / 2f
            activeLength = prefs.getFloat("rocker_total_length", 360f)
            activePeakBias = if (isVolumeUp) 0.25f else 0.75f
        } else {
            activeCenterY = if (isVolumeUp) volUpY else volDownY
            activeLength = prefs.getFloat("single_btn_length", 160f)
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
            dampingRatio = if (target == 0f) springDamping else 0.88f
        }
        springAnim.start()
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

        if (unifiedRocker) {
            drawUnifiedMechanicalRocker(edgeX, peakX, startY, endY, isVolumeUpActive)
        } else {
            when (shapeProfile) {
                0 -> drawPrecisionCurvedSquircle(edgeX, peakX, startY, endY)
                1 -> drawOrganicTeardrop(edgeX, peakX, startY, endY)
                2 -> drawGeometricPill(edgeX, peakX, startY, endY)
                3 -> drawBeveledWedge(edgeX, peakX, startY, endY)
                4 -> drawParabolicArch(edgeX, peakX, startY, endY)
                5 -> drawCustomInterpolatedShape(edgeX, peakX, startY, endY)
            }
        }

        canvas.drawPath(path, paint)
    }

    private fun drawUnifiedMechanicalRocker(
        edgeX: Float,
        peakX: Float,
        startY: Float,
        endY: Float,
        isUpPressed: Boolean
    ) {
        val rockerHeight = endY - startY
        val crestTopY = startY + (rockerHeight * 0.22f)
        val crestBottomY = startY + (rockerHeight * 0.78f)

        val topPeakX = if (isUpPressed) peakX else edgeX + ((peakX - edgeX) * 0.30f)
        val botPeakX = if (isUpPressed) edgeX + ((peakX - edgeX) * 0.30f) else peakX

        path.moveTo(edgeX, startY)
        path.cubicTo(
            edgeX, startY + (rockerHeight * 0.08f),
            topPeakX, crestTopY - (rockerHeight * 0.06f),
            topPeakX, crestTopY
        )
        val saddleX = edgeX + ((peakX - edgeX) * 0.22f)
        val midY = (startY + endY) / 2f
        path.cubicTo(
            topPeakX, midY - (rockerHeight * 0.12f),
            saddleX, midY,
            botPeakX, crestBottomY - (rockerHeight * 0.12f)
        )
        path.cubicTo(
            botPeakX, crestBottomY + (rockerHeight * 0.06f),
            edgeX, endY - (rockerHeight * 0.08f),
            edgeX, endY
        )
        path.close()
    }

    private fun drawPrecisionCurvedSquircle(edgeX: Float, peakX: Float, startY: Float, endY: Float) {
        val h = endY - startY
        val flatHalf = h * 0.18f
        val midY = (startY + endY) / 2f
        val flatTop = midY - flatHalf
        val flatBot = midY + flatHalf

        path.moveTo(edgeX, startY)
        path.cubicTo(edgeX, startY + (h * 0.2f), peakX, flatTop - (h * 0.1f), peakX, flatTop)
        path.lineTo(peakX, flatBot)
        path.cubicTo(peakX, flatBot + (h * 0.1f), edgeX, endY - (h * 0.2f), edgeX, endY)
        path.close()
    }

    private fun drawOrganicTeardrop(edgeX: Float, peakX: Float, startY: Float, endY: Float) {
        val midY = (startY + endY) / 2f
        val h = endY - startY
        path.moveTo(edgeX, startY)
        path.cubicTo(edgeX, startY + (h * 0.35f), peakX, midY - (h * 0.2f), peakX, midY)
        path.cubicTo(peakX, midY + (h * 0.2f), edgeX, endY - (h * 0.35f), edgeX, endY)
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

    private fun drawParabolicArch(edgeX: Float, peakX: Float, startY: Float, endY: Float) {
        path.moveTo(edgeX, startY)
        path.cubicTo(peakX, startY, peakX, endY, edgeX, endY)
        path.close()
    }

    private fun drawCustomInterpolatedShape(edgeX: Float, peakX: Float, startY: Float, endY: Float) {
        val raw = prefs.getString("custom_points_data", null)
        if (raw.isNullOrEmpty()) {
            drawPrecisionCurvedSquircle(edgeX, peakX, startY, endY)
            return
        }
        val pairs = raw.split(";").filter { it.contains(",") }
        if (pairs.size < 3) {
            drawPrecisionCurvedSquircle(edgeX, peakX, startY, endY)
            return
        }

        path.moveTo(edgeX, startY)
        val h = endY - startY
        val depthSign = if (isRightEdge) -1f else 1f

        for (p in pairs) {
            val parts = p.split(",")
            val normY = parts[0].toFloatOrNull() ?: continue
            val normDepth = parts[1].toFloatOrNull() ?: continue
            val px = edgeX + (depthSign * normDepth * currentDepth)
            val py = startY + (normY * h)
            path.lineTo(px, py)
        }
        path.lineTo(edgeX, endY)
        path.close()
    }
}
