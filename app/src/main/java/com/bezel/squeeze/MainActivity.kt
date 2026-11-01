package com.bezel.squeeze

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var permCard: MaterialCardView
    private lateinit var statusBadge: TextView
    private lateinit var rockerSliderBox: LinearLayout
    private lateinit var splitSliderBox: LinearLayout
    private lateinit var powerControlsBox: LinearLayout
    private lateinit var drawCustomBtn: Button
    private lateinit var livePreviewView: PreviewCanvasView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)

        val root = ScrollView(this).apply { setPadding(32, 40, 32, 60) }
        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(mainLayout)

        fun notifyEngine() {
            sendBroadcast(Intent("com.bezel.squeeze.UPDATE_SETTINGS").setPackage(packageName))
            livePreviewView.reloadAndPreview()
        }

        // --- Status Badge & Auto-Hiding Permission Card ---
        statusBadge = TextView(this).apply {
            text = "● Bezel Engine Active"
            textSize = 15f
            setTextColor(0xFF34C759.toInt())
            setPadding(0, 0, 0, 16)
        }
        mainLayout.addView(statusBadge)

        permCard = MaterialCardView(this).apply {
            radius = 24f
            setCardBackgroundColor(0xFF1E1E1E.toInt())
            val inner = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                val tip = TextView(this@MainActivity).apply {
                    text = "Accessibility service is disabled. Enable it to intercept hardware button presses."
                    textSize = 14f
                    setTextColor(0xFFFF9F0A.toInt())
                    setPadding(0, 0, 0, 16)
                }
                val btn = Button(this@MainActivity).apply {
                    text = "Enable Hardware Service"
                    setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
                addView(tip)
                addView(btn)
            }
            addView(inner)
        }
        mainLayout.addView(permCard)

        // --- Live Preview Component ---
        addCard(mainLayout, "Live Display Preview") { container ->
            livePreviewView = PreviewCanvasView(this@MainActivity)
            livePreviewView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 130).toInt()
            )
            val testBtn = Button(this@MainActivity).apply {
                text = "Tap to Test Squeeze Animation"
                setOnClickListener { livePreviewView.triggerTestBulge() }
            }
            container.addView(livePreviewView)
            container.addView(testBtn)
        }

        // --- Color Customizer ---
        addCard(mainLayout, "Bezel Color & Finish") { container ->
            val colorPickerRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val colorIndicator = View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(90, 90)
                setBackgroundColor(prefs.getInt("bulge_color", Color.BLACK))
            }

            val presetGroup = RadioGroup(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val presets = listOf(
                "OLED Pitch Black" to Color.BLACK,
                "Titanium Slate" to 0xFF3A3A3C.toInt(),
                "Electric Blue" to 0xFF0A84FF.toInt(),
                "Crimson Red" to 0xFFFF3B30.toInt()
            )
            for ((label, col) in presets) {
                val rb = RadioButton(this@MainActivity).apply {
                    text = label
                    isChecked = prefs.getInt("bulge_color", Color.BLACK) == col
                    setOnClickListener {
                        prefs.edit().putInt("bulge_color", col).apply()
                        colorIndicator.setBackgroundColor(col)
                        notifyEngine()
                    }
                }
                presetGroup.addView(rb)
            }

            val hexBtn = Button(this@MainActivity).apply {
                text = "Enter Custom HEX"
                setOnClickListener {
                    showHexInputDialog { pickedColor ->
                        prefs.edit().putInt("bulge_color", pickedColor).apply()
                        colorIndicator.setBackgroundColor(pickedColor)
                        notifyEngine()
                    }
                }
            }

            colorPickerRow.addView(colorIndicator)
            val optBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 0, 0, 0)
                addView(presetGroup)
                addView(hexBtn)
            }
            colorPickerRow.addView(optBox)
            container.addView(colorPickerRow)
        }

        // --- Profile & Shapes ---
        addCard(mainLayout, "Bezel Contour Profile") { container ->
            val spinner = Spinner(this@MainActivity)
            val shapes = arrayOf(
                "Precision Curved Squircle (Default)",
                "Organic Teardrop",
                "Geometric Pill",
                "Beveled Wedge",
                "Parabolic Arch",
                "Custom Parametric Curve"
            )
            spinner.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, shapes)
            spinner.setSelection(prefs.getInt("shape_profile", 0))

            drawCustomBtn = Button(this@MainActivity).apply {
                text = "Open Curve Designer Canvas"
                visibility = if (prefs.getInt("shape_profile", 0) == 5) View.VISIBLE else View.GONE
                setOnClickListener { showDrawShapeDialog(::notifyEngine) }
            }

            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    prefs.edit().putInt("shape_profile", pos).apply()
                    drawCustomBtn.visibility = if (pos == 5) View.VISIBLE else View.GONE
                    notifyEngine()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
            container.addView(spinner)
            container.addView(drawCustomBtn)
        }

        // --- Hardware Mechanics ---
        addCard(mainLayout, "Button Mechanics & Edge Side") { container ->
            val edgeSwitch = Switch(this@MainActivity).apply {
                text = "Buttons on Right Chassis Edge (Off = Left)"
                isChecked = prefs.getBoolean("is_right_side", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("is_right_side", isChecked).apply()
                    notifyEngine()
                }
            }
            container.addView(edgeSwitch)

            rockerSliderBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            splitSliderBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }

            val rockerSwitch = Switch(this@MainActivity).apply {
                text = "Unified Rocker Bar Architecture"
                isChecked = prefs.getBoolean("unified_rocker", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("unified_rocker", isChecked).apply()
                    rockerSliderBox.visibility = if (isChecked) View.VISIBLE else View.GONE
                    splitSliderBox.visibility = if (isChecked) View.GONE else View.VISIBLE
                    notifyEngine()
                }
            }
            container.addView(rockerSwitch)

            addFineSlider(rockerSliderBox, "Total Rocker Length", "rocker_total_length", 360f, 10f, "px", ::notifyEngine)
            container.addView(rockerSliderBox)

            addFineSlider(splitSliderBox, "Individual Button Length", "single_btn_length", 160f, 10f, "px", ::notifyEngine)
            container.addView(splitSliderBox)

            val isRocker = prefs.getBoolean("unified_rocker", true)
            rockerSliderBox.visibility = if (isRocker) View.VISIBLE else View.GONE
            splitSliderBox.visibility = if (isRocker) View.GONE else View.VISIBLE
        }

        // --- Power Key Engine ---
        addCard(mainLayout, "Chassis Power Key") { container ->
            val powerSwitch = Switch(this@MainActivity).apply {
                text = "Enable Power Key Bezel Reaction"
                isChecked = prefs.getBoolean("enable_power_button", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("enable_power_button", isChecked).apply()
                    powerControlsBox.visibility = if (isChecked) View.VISIBLE else View.GONE
                    notifyEngine()
                }
            }
            container.addView(powerSwitch)

            powerControlsBox = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (prefs.getBoolean("enable_power_button", true)) View.VISIBLE else View.GONE
            }
            val powerSideSwitch = Switch(this@MainActivity).apply {
                text = "Power Key on Right Edge"
                isChecked = prefs.getBoolean("power_is_right", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("power_is_right", isChecked).apply()
                    notifyEngine()
                }
            }
            powerControlsBox.addView(powerSideSwitch)
            addFineSlider(powerControlsBox, "Power Key Y Placement", "power_y", 1200f, 10f, "px", ::notifyEngine)
            addFineSlider(powerControlsBox, "Power Key Length", "power_length", 180f, 10f, "px", ::notifyEngine)
            container.addView(powerControlsBox)
        }

        // --- Calibration & Dynamic Ranges ---
        addCard(mainLayout, "Uncapped Dimension Calibration") { container ->
            addFineSlider(container, "Volume Up Peak Y", "vol_up_y", 720f, 10f, "px", ::notifyEngine)
            addFineSlider(container, "Volume Down Peak Y", "vol_down_y", 960f, 10f, "px", ::notifyEngine)
            addFineSlider(container, "Inward Push Depth", "bulge_depth", 40f, 2f, "px", ::notifyEngine)
        }

        // --- Latency & Dynamics ---
        addCard(mainLayout, "Input Latency & Dynamics") { container ->
            addFineSlider(container, "Hardware Latency Override", "input_latency_ms", 0f, 5f, "ms", ::notifyEngine)
            addFineSlider(container, "Spring Attack Speed (Stiffness)", "speed_stiffness", 3400f, 100f, "Hz", ::notifyEngine)
            addFineSlider(container, "Snap Damping Elasticity", "bounce_damping", 0.75f, 0.05f, "", ::notifyEngine)
        }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun addCard(parent: LinearLayout, title: String, content: (LinearLayout) -> Unit) {
        val card = MaterialCardView(this).apply {
            radius = 24f
            setCardBackgroundColor(0xFF161618.toInt())
            val marginParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            layoutParams = marginParams

            val inner = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 28, 28, 28)
                val head = TextView(this@MainActivity).apply {
                    text = title
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(0, 0, 0, 16)
                }
                addView(head)
                content(this)
            }
            addView(inner)
        }
        parent.addView(card)
    }

    private fun addFineSlider(
        parent: LinearLayout,
        label: String,
        key: String,
        def: Float,
        step: Float,
        unit: String,
        onChanged: () -> Unit
    ) {
        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val currentVal = prefs.getFloat(key, def)

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = TextView(this).apply {
            text = "$label: ${String.format("%.1f", currentVal)} $unit"
            textSize = 14f
            setTextColor(0xFFCCCCCC.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val directEditBtn = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Edit Value"
            textSize = 12f
            setOnClickListener {
                showDirectNumberDialog(label, prefs.getFloat(key, def)) { enteredVal ->
                    prefs.edit().putFloat(key, enteredVal).apply()
                    titleTv.text = "$label: ${String.format("%.1f", enteredVal)} $unit"
                    onChanged()
                }
            }
        }
        headerRow.addView(titleTv)
        headerRow.addView(directEditBtn)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 16)
        }

        fun adjust(delta: Float) {
            val newVal = (prefs.getFloat(key, def) + delta).coerceAtLeast(0f)
            prefs.edit().putFloat(key, newVal).apply()
            titleTv.text = "$label: ${String.format("%.1f", newVal)} $unit"
            onChanged()
        }

        val minusBig = Button(this).apply {
            text = "-10"
            layoutParams = LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { adjust(-step * 5) }
        }
        val minusSmall = Button(this).apply {
            text = "-"
            layoutParams = LinearLayout.LayoutParams(95, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { adjust(-step) }
        }
        val plusSmall = Button(this).apply {
            text = "+"
            layoutParams = LinearLayout.LayoutParams(95, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { adjust(step) }
        }
        val plusBig = Button(this).apply {
            text = "+10"
            layoutParams = LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { adjust(step * 5) }
        }

        btnRow.addView(minusBig)
        btnRow.addView(minusSmall)
        btnRow.addView(plusSmall)
        btnRow.addView(plusBig)

        parent.addView(headerRow)
        parent.addView(btnRow)
    }

    private fun showDirectNumberDialog(label: String, current: Float, onDone: (Float) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(current.toString())
        }
        AlertDialog.Builder(this)
            .setTitle("Set $label")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                input.text.toString().toFloatOrNull()?.let { onDone(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHexInputDialog(onColorChosen: (Int) -> Unit) {
        val input = EditText(this).apply {
            hint = "#FF0000 or #000000"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("Custom HEX Color")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                try {
                    val color = Color.parseColor(input.text.toString().trim())
                    onColorChosen(color)
                } catch (_: Exception) {
                    Toast.makeText(this, "Invalid HEX code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePermissionStatus() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledList = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isRunning = enabledList.any { it.resolveInfo.serviceInfo.packageName == packageName }

        if (isRunning) {
            permCard.visibility = View.GONE
            statusBadge.visibility = View.VISIBLE
        } else {
            permCard.visibility = View.VISIBLE
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
            setBackgroundColor(0xFF1E1E1E.toInt())
        }

        val title = TextView(this).apply {
            text = "Trace Custom Parametric Contour"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        root.addView(title)

        val drawView = CustomDrawingView(this)
        drawView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.density * 240).toInt()
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
            text = "Save Profile"
            setOnClickListener {
                val pointData = drawView.exportNormalizedCurve()
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

class PreviewCanvasView(context: Context) : View(context) {
    private var depth = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF444446.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val path = Path()

    fun reloadAndPreview() {
        val prefs = context.getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        paint.color = prefs.getInt("bulge_color", Color.BLACK)
        depth = prefs.getFloat("bulge_depth", 40f).coerceIn(10f, 60f)
        invalidate()
    }

    fun triggerTestBulge() {
        depth = 50f
        invalidate()
        postDelayed({
            depth = 0f
            invalidate()
        }, 320)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF2C2C2E.toInt())
        val edgeX = width.toFloat()
        canvas.drawLine(edgeX, 0f, edgeX, height.toFloat(), framePaint)

        if (depth > 0f) {
            path.reset()
            val startY = height * 0.2f
            val endY = height * 0.8f
            val peakX = edgeX - depth
            path.moveTo(edgeX, startY)
            path.cubicTo(edgeX, height * 0.35f, peakX, height * 0.4f, peakX, height * 0.5f)
            path.cubicTo(peakX, height * 0.6f, edgeX, height * 0.65f, edgeX, endY)
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}

class CustomDrawingView(context: Context) : View(context) {
    private val points = mutableListOf<PointF>()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A84FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    fun clear() {
        points.clear()
        invalidate()
    }

    fun exportNormalizedCurve(): String {
        if (points.size < 3) return ""
        val sorted = points.sortedBy { it.y }
        val minY = sorted.first().y
        val maxY = sorted.last().y
        val h = (maxY - minY).coerceAtLeast(1f)
        val w = width.toFloat().coerceAtLeast(1f)

        return sorted.map {
            val normY = ((it.y - minY) / h).coerceIn(0f, 1f)
            val normDepth = (it.x / w).coerceIn(0f, 1f)
            "$normY,$normDepth"
        }.joinToString(";")
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                points.clear()
                points.add(PointF(event.x, event.y))
                invalidate()
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                points.add(PointF(event.x, event.y))
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF121212.toInt())
        if (points.size > 1) {
            for (i in 0 until points.size - 1) {
                canvas.drawLine(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, strokePaint)
            }
        }
    }
}
