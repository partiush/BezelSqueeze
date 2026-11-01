package com.ios.bezelsqueeze

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.SpringForce

class MainActivity : AppCompatActivity() {

    private lateinit var permContainer: LinearLayout
    private lateinit var statusBadge: TextView
    private lateinit var rockerSliderBox: LinearLayout
    private lateinit var splitSliderBox: LinearLayout
    private lateinit var powerControlsBox: LinearLayout
    private lateinit var drawCustomBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()

        val root = ScrollView(this).apply { setPadding(36, 48, 36, 64) }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(container)

        fun notifyEngine() {
            sendBroadcast(Intent("com.ios.bezelsqueeze.UPDATE_SETTINGS").setPackage(packageName))
        }

        // --- Status Badge & Auto-Hiding Permission Panel ---
        statusBadge = TextView(this).apply {
            text = "● iOS Squeeze Engine Active"
            textSize = 15f
            setTextColor(0xFF34C759.toInt())
            setPadding(0, 0, 0, 16)
        }
        container.addView(statusBadge)

        permContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val tip = TextView(this@MainActivity).apply {
                text = "Engine is off. Turn on Accessibility to track hardware clicks."
                textSize = 14f
                setTextColor(0xFFFF9500.toInt())
                setPadding(0, 0, 0, 12)
            }
            val btn = Button(this@MainActivity).apply {
                text = "Grant Accessibility Engine"
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            addView(tip)
            addView(btn)
            setPadding(0, 0, 0, 24)
        }
        container.addView(permContainer)

        fun addSectionHeader(title: String) {
            container.addView(TextView(this).apply {
                text = title
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 36, 0, 8)
            })
        }

        // --- Infinite Slider with +/- Fine Tuning Buttons ---
        fun addFineSlider(
            parent: LinearLayout,
            label: String,
            key: String,
            min: Float,
            max: Float,
            def: Float,
            step: Float = 2f,
            unit: String = "px"
        ) {
            val currentVal = prefs.getFloat(key, def)
            val titleTv = TextView(this).apply {
                text = "$label: ${currentVal.toInt()} $unit"
                textSize = 14f
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 16)
            }

            val seek = SeekBar(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                this.max = ((max - min) / step).toInt()
                progress = ((currentVal - min) / step).toInt()
            }

            fun updateVal(newVal: Float) {
                val clamped = newVal.coerceIn(min, max)
                titleTv.text = "$label: ${clamped.toInt()} $unit"
                seek.progress = ((clamped - min) / step).toInt()
                prefs.edit().putFloat(key, clamped).apply()
                notifyEngine()
            }

            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) updateVal(min + (p * step))
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })

            val minusBtn = Button(this).apply {
                text = "-"
                layoutParams = LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener { updateVal(prefs.getFloat(key, def) - step) }
            }

            val plusBtn = Button(this).apply {
                text = "+"
                layoutParams = LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener { updateVal(prefs.getFloat(key, def) + step) }
            }

            row.addView(minusBtn)
            row.addView(seek)
            row.addView(plusBtn)

            parent.addView(titleTv)
            parent.addView(row)
        }

        // --- 1. Shape Engine ---
        addSectionHeader("1. Bezel Profile & Shapes")
        val spinner = Spinner(this)
        val shapeNames = arrayOf(
            "iOS 18 Squircle (Apple Default)",
            "Organic Teardrop",
            "Pill / Oval",
            "Sharp Bevel",
            "Curved Crescent",
            "Liquid Slime",
            "Custom Drawn Canvas"
        )
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, shapeNames)
        spinner.setSelection(prefs.getInt("shape_profile", 0))

        drawCustomBtn = Button(this).apply {
            text = "✏️ Open Custom Shape Canvas"
            visibility = if (prefs.getInt("shape_profile", 0) == 6) View.VISIBLE else View.GONE
            setOnClickListener { showDrawShapeDialog(::notifyEngine) }
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit().putInt("shape_profile", pos).apply()
                drawCustomBtn.visibility = if (pos == 6) View.VISIBLE else View.GONE
                notifyEngine()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        container.addView(spinner)
        container.addView(drawCustomBtn)

        // --- 2. Button Architecture ---
        addSectionHeader("2. Physical Key Architecture")
        val edgeSwitch = Switch(this).apply {
            text = "Volume Buttons on Right Edge (Off = Left)"
            isChecked = prefs.getBoolean("is_right_side", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("is_right_side", isChecked).apply()
                notifyEngine()
            }
        }
        container.addView(edgeSwitch)

        rockerSliderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        splitSliderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val rockerSwitch = Switch(this).apply {
            text = "Unified Rocker Bar (iOS Style)"
            isChecked = prefs.getBoolean("unified_rocker", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("unified_rocker", isChecked).apply()
                rockerSliderBox.visibility = if (isChecked) View.VISIBLE else View.GONE
                splitSliderBox.visibility = if (isChecked) View.GONE else View.VISIBLE
                notifyEngine()
            }
        }
        container.addView(rockerSwitch)

        // --- 3. Power Button Integration ---
        addSectionHeader("3. Power Button Engine")
        val powerSwitch = Switch(this).apply {
            text = "Enable Power Button Bezel Squeeze"
            isChecked = prefs.getBoolean("enable_power_button", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("enable_power_button", isChecked).apply()
                powerControlsBox.visibility = if (isChecked) View.VISIBLE else View.GONE
                notifyEngine()
            }
        }
        container.addView(powerSwitch)

        powerControlsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (prefs.getBoolean("enable_power_button", true)) View.VISIBLE else View.GONE
        }
        val powerSideSwitch = Switch(this).apply {
            text = "Power Button on Right Edge"
            isChecked = prefs.getBoolean("power_is_right", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("power_is_right", isChecked).apply()
                notifyEngine()
            }
        }
        powerControlsBox.addView(powerSideSwitch)
        addFineSlider(powerControlsBox, "Power Key Y Position", "power_y", 50f, screenHeight, 1200f, 4f)
        addFineSlider(powerControlsBox, "Power Key Length", "power_length", 50f, 600f, 180f, 4f)
        container.addView(powerControlsBox)

        // --- 4. Infinite Sizing & Depths ---
        addSectionHeader("4. Infinite Size & Calibration")
        addFineSlider(container, "Volume Up Peak Y", "vol_up_y", 50f, screenHeight, 720f, 2f)
        addFineSlider(container, "Volume Down Peak Y", "vol_down_y", 50f, screenHeight, 920f, 2f)
        addFineSlider(container, "Inward Push Depth", "bulge_depth", 5f, 200f, 40f, 1f)

        addFineSlider(rockerSliderBox, "Total Rocker Length", "rocker_total_length", 80f, 1200f, 320f, 4f)
        container.addView(rockerSliderBox)

        addFineSlider(splitSliderBox, "Individual Key Length", "single_btn_length", 40f, 600f, 150f, 2f)
        container.addView(splitSliderBox)

        // --- 5. Speed & Elastic Physics ---
        addSectionHeader("5. Speed & Spring Elasticity")
        addFineSlider(container, "Attack Speed (Stiffness)", "speed_stiffness", 500f, 10000f, 3200f, 100f, "Hz")
        addFineSlider(container, "Snap Bounciness (Low = Snappy)", "bounce_damping", 0.3f, 1.0f, 0.75f, 0.02f, "")

        val isRocker = prefs.getBoolean("unified_rocker", true)
        rockerSliderBox.visibility = if (isRocker) View.VISIBLE else View.GONE
        splitSliderBox.visibility = if (isRocker) View.GONE else View.VISIBLE

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledList = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isRunning = enabledList.any { it.resolveInfo.serviceInfo.packageName == packageName }

        if (isRunning) {
            permContainer.visibility = View.GONE
            statusBadge.visibility = View.VISIBLE
        } else {
            permContainer.visibility = View.VISIBLE
            statusBadge.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showDrawShapeDialog(onSaved: () -> Unit) {
        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Draw Your Bulge Contour"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        root.addView(title)

        val drawView = CustomDrawingView(this)
        drawView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.density * 260).toInt()
        )
        root.addView(drawView)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        val clearBtn = Button(this).apply {
            text = "Clear"
            setOnClickListener { drawView.clear() }
        }
        val saveBtn = Button(this).apply {
            text = "Save Shape"
            setOnClickListener {
                val pointData = drawView.exportNormalizedPoints()
                prefs.edit().putString("custom_points_data", pointData).apply()
                onSaved()
                dialog.dismiss()
            }
        }
        btnRow.addView(clearBtn)
        btnRow.addView(saveBtn)
        root.addView(btnRow)

        dialog.setContentView(root)
        dialog.show()
    }
}

class CustomDrawingView(context: Context) : View(context) {
    private val points = mutableListOf<PointF>()
    private val path = Path()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val bgPaint = Paint().apply { color = 0xFF222222.toInt() }

    init {
        setBackgroundColor(0xFF222222.toInt())
    }

    fun clear() {
        points.clear()
        path.reset()
        invalidate()
    }

    fun exportNormalizedPoints(): String {
        if (points.isEmpty()) return ""
        val sorted = points.sortedBy { it.y }
        val minY = sorted.first().y
        val maxY = sorted.last().y
        val h = (maxY - minY).coerceAtLeast(1f)
        val w = width.toFloat().coerceAtLeast(1f)

        return sorted.map {
            val normY = ((it.y - minY) / h).coerceIn(0f, 1f)
            val normX = (it.x / w).coerceIn(0f, 1f)
            "$normY,$normX"
        }.joinToString(";")
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                points.clear()
                path.reset()
                path.moveTo(event.x, event.y)
                points.add(PointF(event.x, event.y))
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(event.x, event.y)
                points.add(PointF(event.x, event.y))
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        canvas.drawPath(path, strokePaint)
    }
}
