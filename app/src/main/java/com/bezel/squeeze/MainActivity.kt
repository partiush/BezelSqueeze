package com.bezel.squeeze

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var cardPerm: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)

        fun notifyEngine() {
            sendBroadcast(Intent("com.bezel.squeeze.UPDATE_SETTINGS").setPackage(packageName))
        }

        // Views
        tvStatus = findViewById(R.id.tvStatus)
        cardPerm = findViewById(R.id.cardPerm)
        val btnPerm: MaterialButton = findViewById(R.id.btnPerm)
        val btnHoldTest: MaterialButton = findViewById(R.id.btnHoldTest)
        val spinnerShape: Spinner = findViewById(R.id.spinnerShape)
        val btnOpenDraw: MaterialButton = findViewById(R.id.btnOpenDraw)
        val colorPreview: View = findViewById(R.id.colorPreview)
        val btnPickColor: MaterialButton = findViewById(R.id.btnPickColor)

        val switchRightEdge: MaterialSwitch = findViewById(R.id.switchRightEdge)
        val switchUnified: MaterialSwitch = findViewById(R.id.switchUnified)
        val boxUnified: LinearLayout = findViewById(R.id.boxUnified)
        val boxSplit: LinearLayout = findViewById(R.id.boxSplit)
        val tvRockerLen: TextView = findViewById(R.id.tvRockerLen)
        val sliderRockerLen: Slider = findViewById(R.id.sliderRockerLen)
        val tvSingleLen: TextView = findViewById(R.id.tvSingleLen)
        val sliderSingleLen: Slider = findViewById(R.id.sliderSingleLen)

        val tvVolUpY: TextView = findViewById(R.id.tvVolUpY)
        val sliderVolUpY: Slider = findViewById(R.id.sliderVolUpY)
        val tvVolDownY: TextView = findViewById(R.id.tvVolDownY)
        val sliderVolDownY: Slider = findViewById(R.id.sliderVolDownY)
        val tvDepth: TextView = findViewById(R.id.tvDepth)
        val sliderDepth: Slider = findViewById(R.id.sliderDepth)
        val tvSpeed: TextView = findViewById(R.id.tvSpeed)
        val sliderSpeed: Slider = findViewById(R.id.sliderSpeed)

        val tvExtraKeyStatus: TextView = findViewById(R.id.tvExtraKeyStatus)
        val btnMapExtra: MaterialButton = findViewById(R.id.btnMapExtra)
        val tvExtraY: TextView = findViewById(R.id.tvExtraY)
        val sliderExtraY: Slider = findViewById(R.id.sliderExtraY)

        // Permission Button
        btnPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // Live Hold Test
        btnHoldTest.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sendBroadcast(Intent("com.bezel.squeeze.TEST_PRESS").setPackage(packageName))
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    sendBroadcast(Intent("com.bezel.squeeze.TEST_RELEASE").setPackage(packageName))
                    true
                }
                else -> false
            }
        }

        // Shape Spinner
        val shapes = arrayOf("Smooth Curved Lip", "Organic Teardrop", "Rounded Pill", "Beveled Edge", "Custom Freehand Drawing")
        spinnerShape.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, shapes)
        spinnerShape.setSelection(prefs.getInt("shape_type", 0))
        btnOpenDraw.visibility = if (prefs.getInt("shape_type", 0) == 4) View.VISIBLE else View.GONE

        spinnerShape.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit().putInt("shape_type", pos).apply()
                btnOpenDraw.visibility = if (pos == 4) View.VISIBLE else View.GONE
                notifyEngine()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        btnOpenDraw.setOnClickListener {
            showDrawingDialog(::notifyEngine)
        }

        // Color Picker
        colorPreview.setBackgroundColor(prefs.getInt("bulge_color", Color.BLACK))
        btnPickColor.setOnClickListener {
            showColorDialog { col ->
                prefs.edit().putInt("bulge_color", col).apply()
                colorPreview.setBackgroundColor(col)
                notifyEngine()
            }
        }

        // Switches
        switchRightEdge.isChecked = prefs.getBoolean("is_right_edge", true)
        switchRightEdge.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_right_edge", isChecked).apply()
            notifyEngine()
        }

        val isUnified = prefs.getBoolean("is_unified_rocker", true)
        switchUnified.isChecked = isUnified
        boxUnified.visibility = if (isUnified) View.VISIBLE else View.GONE
        boxSplit.visibility = if (isUnified) View.GONE else View.VISIBLE

        switchUnified.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_unified_rocker", isChecked).apply()
            boxUnified.visibility = if (isChecked) View.VISIBLE else View.GONE
            boxSplit.visibility = if (isChecked) View.GONE else View.VISIBLE
            notifyEngine()
        }

        // Helper to bind sliders safely
        fun bindSlider(slider: Slider, textLabel: TextView, labelText: String, key: String, defVal: Float, unit: String) {
            val saved = prefs.getFloat(key, defVal).coerceIn(slider.valueFrom, slider.valueTo)
            val snapped = slider.valueFrom + Math.round((saved - slider.valueFrom) / slider.stepSize) * slider.stepSize
            slider.value = snapped
            textLabel.text = "$labelText: ${snapped.toInt()} $unit"

            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    prefs.edit().putFloat(key, value).apply()
                    textLabel.text = "$labelText: ${value.toInt()} $unit"
                    notifyEngine()
                }
            }
        }

        bindSlider(sliderRockerLen, tvRockerLen, "Total Bar Length", "rocker_length", 380f, "px")
        bindSlider(sliderSingleLen, tvSingleLen, "Separate Button Length", "single_btn_length", 160f, "px")
        bindSlider(sliderVolUpY, tvVolUpY, "Volume Up Height", "vol_up_y", 720f, "px")
        bindSlider(sliderVolDownY, tvVolDownY, "Volume Down Height", "vol_down_y", 960f, "px")
        bindSlider(sliderDepth, tvDepth, "Bulge Depth", "bulge_depth", 40f, "px")
        bindSlider(sliderSpeed, tvSpeed, "Inward Speed", "press_speed_ms", 85f, "ms")

        // Extra Hardware Key
        val mappedCode = prefs.getInt("extra_button_code", -1)
        if (mappedCode != -1) tvExtraKeyStatus.text = "Mapped Key Code: $mappedCode"
        btnMapExtra.setOnClickListener {
            btnMapExtra.text = "Press your button now..."
            BezelAccessibilityService.isListeningForKey = true
            BezelAccessibilityService.onKeyDetected = { code ->
                runOnUiThread {
                    prefs.edit().putInt("extra_button_code", code).apply()
                    tvExtraKeyStatus.text = "Mapped Key Code: $code"
                    btnMapExtra.text = "Map Extra Button"
                    notifyEngine()
                    Toast.makeText(this, "Saved Button: $code", Toast.LENGTH_SHORT).show()
                }
            }
        }
        bindSlider(sliderExtraY, tvExtraY, "Extra Button Height", "extra_y", 500f, "px")
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledList = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isRunning = enabledList.any { it.resolveInfo.serviceInfo.packageName == packageName }

        cardPerm.visibility = if (isRunning) View.GONE else View.VISIBLE
        tvStatus.visibility = if (isRunning) View.VISIBLE else View.GONE
    }

    private fun showColorDialog(onColorChosen: (Int) -> Unit) {
        val colors = arrayOf("Black", "Dark Gray", "Blue", "Red", "Custom HEX")
        val values = intArrayOf(Color.BLACK, 0xFF333333.toInt(), 0xFF007AFF.toInt(), 0xFFFF3B30.toInt(), 0)

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Bezel Color")
            .setItems(colors) { _, which ->
                if (which == 4) {
                    val input = EditText(this).apply { hint = "#000000" }
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Enter HEX Color")
                        .setView(input)
                        .setPositiveButton("Apply") { _, _ ->
                            try {
                                onColorChosen(Color.parseColor(input.text.toString().trim()))
                            } catch (_: Exception) {
                                Toast.makeText(this, "Invalid color code", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    onColorChosen(values[which])
                }
            }
            .show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showDrawingDialog(onSaved: () -> Unit) {
        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val dialog = Dialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF1E1E1E.toInt())
        }

        val title = TextView(this).apply {
            text = "Draw Your Custom Bezel Curve"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        val sub = TextView(this).apply {
            text = "Draw an outward curved line from top to bottom. It will become the contour of your bezel squeeze."
            textSize = 13f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 0, 0, 16)
        }
        container.addView(title)
        container.addView(sub)

        val drawCanvas = SimpleDrawingCanvas(this)
        drawCanvas.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.density * 260).toInt()
        )
        container.addView(drawCanvas)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }
        val clearBtn = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Clear"
            setOnClickListener { drawCanvas.reset() }
        }
        val saveBtn = MaterialButton(this).apply {
            text = "Save & Apply"
            setOnClickListener {
                val data = drawCanvas.getNormalizedData()
                prefs.edit().putString("custom_drawing_points", data).apply()
                onSaved()
                dialog.dismiss()
            }
        }
        btnRow.addView(clearBtn)
        btnRow.addView(saveBtn)
        container.addView(btnRow)

        dialog.setContentView(container)
        dialog.show()
    }
}

class SimpleDrawingCanvas(context: Context) : View(context) {
    private val points = mutableListOf<PointF>()
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A84FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A3A3C.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun reset() {
        points.clear()
        path.reset()
        invalidate()
    }

    fun getNormalizedData(): String {
        if (points.size < 2) return ""

        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val heightSpan = (maxY - minY).coerceAtLeast(1f)

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val widthSpan = (maxX - minX).coerceAtLeast(1f)

        return points.map {
            val normY = ((it.y - minY) / heightSpan).coerceIn(0f, 1f)
            val normX = ((it.x - minX) / widthSpan).coerceIn(0f, 1f)
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
            MotionEvent.ACTION_UP -> {
                path.lineTo(event.x, event.y)
                points.add(PointF(event.x, event.y))
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF121212.toInt())
        canvas.drawLine(16f, 0f, 16f, height.toFloat(), guidePaint)
        canvas.drawPath(path, paint)
    }
}
