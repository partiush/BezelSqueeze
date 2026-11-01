package com.bezel.squeeze

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.DisplayMetrics
import android.view.Gravity
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

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

        tvStatus = findViewById(R.id.tvStatus)
        cardPerm = findViewById(R.id.cardPerm)
        val btnPerm: MaterialButton = findViewById(R.id.btnPerm)
        val btnHoldTest: MaterialButton = findViewById(R.id.btnHoldTest)
        val btnTestVolumePanel: MaterialButton = findViewById(R.id.btnTestVolumePanel)
        val spinnerPanelStyle: Spinner = findViewById(R.id.spinnerPanelStyle)
        val spinnerShape: Spinner = findViewById(R.id.spinnerShape)
        val btnOpenDraw: MaterialButton = findViewById(R.id.btnOpenDraw)
        val colorPreview: View = findViewById(R.id.colorPreview)
        val btnPickColor: MaterialButton = findViewById(R.id.btnPickColor)
        val switchRightEdge: MaterialSwitch = findViewById(R.id.switchRightEdge)
        val switchUnified: MaterialSwitch = findViewById(R.id.switchUnified)
        val switchHaptics: MaterialSwitch = findViewById(R.id.switchHaptics)
        val btnAutoCalibrate: MaterialButton = findViewById(R.id.btnAutoCalibrate)
        val boxSlidersContainer: LinearLayout = findViewById(R.id.boxSlidersContainer)

        val tvExtraKeyStatus: TextView = findViewById(R.id.tvExtraKeyStatus)
        val btnMapExtra: MaterialButton = findViewById(R.id.btnMapExtra)
        val btnRenameExtra: MaterialButton = findViewById(R.id.btnRenameExtra)
        val btnDeleteExtra: MaterialButton = findViewById(R.id.btnDeleteExtra)

        btnPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

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

        btnTestVolumePanel.setOnClickListener {
            sendBroadcast(Intent("com.bezel.squeeze.TEST_PANEL").setPackage(packageName))
        }

        val panels = arrayOf("iOS Dynamic Panel", "Samsung OneUI", "Xiaomi HyperOS", "OPPO ColorOS", "Google Pixel")
        spinnerPanelStyle.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, panels)
        spinnerPanelStyle.setSelection(prefs.getInt("volume_panel_style", 0))
        spinnerPanelStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit().putInt("volume_panel_style", pos).apply()
                notifyEngine()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        val shapes = arrayOf("Smooth Squircle", "Organic Teardrop", "Geometric Pill", "Beveled Wedge", "Freehand Drawn Curve")
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

        colorPreview.setBackgroundColor(prefs.getInt("bulge_color", Color.BLACK))
        btnPickColor.setOnClickListener {
            showColorDialog { col ->
                prefs.edit().putInt("bulge_color", col).apply()
                colorPreview.setBackgroundColor(col)
                notifyEngine()
            }
        }

        switchRightEdge.isChecked = prefs.getBoolean("is_right_edge", true)
        switchRightEdge.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_right_edge", isChecked).apply()
            notifyEngine()
        }

        switchUnified.isChecked = prefs.getBoolean("is_unified_rocker", true)
        switchUnified.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("is_unified_rocker", isChecked).apply()
            notifyEngine()
        }

        switchHaptics.isChecked = prefs.getBoolean("enable_haptics", true)
        switchHaptics.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_haptics", isChecked).apply()
        }

        fun populateSliders() {
            boxSlidersContainer.removeAllViews()
            fun addUncappedSlider(label: String, key: String, defVal: Float, step: Float, unit: String) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 10, 0, 10)
                }
                val titleRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val currentVal = prefs.getFloat(key, defVal)
                val titleTv = TextView(this).apply {
                    text = "$label: ${currentVal.toInt()} $unit"
                    textSize = 14f
                    setTextColor(0xFFCCCCCC.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val editBtn = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    text = "Edit"
                    textSize = 12f
                }
                titleRow.addView(titleTv)
                titleRow.addView(editBtn)
                row.addView(titleRow)

                val slider = Slider(this).apply {
                    valueFrom = (currentVal * 0.2f).coerceAtLeast(0f)
                    valueTo = (currentVal * 2.5f).coerceAtLeast(100f)
                    stepSize = step
                    value = currentVal.coerceIn(valueFrom, valueTo)
                    setLabelFormatter { "${it.toInt()} $unit" }
                }

                fun updateValue(newVal: Float) {
                    val clamped = newVal.coerceAtLeast(0f)
                    prefs.edit().putFloat(key, clamped).apply()
                    titleTv.text = "$label: ${clamped.toInt()} $unit"
                    if (clamped in slider.valueFrom..slider.valueTo) slider.value = clamped
                    notifyEngine()
                }

                editBtn.setOnClickListener {
                    val textLayout = TextInputLayout(this).apply { setPadding(40, 16, 40, 0) }
                    val input = TextInputEditText(textLayout.context).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        setText(prefs.getFloat(key, defVal).toInt().toString())
                    }
                    textLayout.addView(input)
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Set $label")
                        .setView(textLayout)
                        .setPositiveButton("Save") { _, _ ->
                            input.text.toString().toFloatOrNull()?.let { updateValue(it) }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }

                slider.addOnChangeListener { _, value, fromUser ->
                    if (fromUser) updateValue(value)
                }

                val stepRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val minusBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "-$step"
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { updateValue(prefs.getFloat(key, defVal) - step) }
                }
                val plusBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "+$step"
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { updateValue(prefs.getFloat(key, defVal) + step) }
                }
                stepRow.addView(minusBtn)
                stepRow.addView(plusBtn)

                row.addView(slider)
                row.addView(stepRow)
                boxSlidersContainer.addView(row)
            }

            addUncappedSlider("Volume Up Height", "vol_up_y", 720f, 10f, "px")
            addUncappedSlider("Volume Down Height", "vol_down_y", 960f, 10f, "px")
            addUncappedSlider("Rocker Total Length", "rocker_length", 380f, 10f, "px")
            addUncappedSlider("Separate Button Length", "single_btn_length", 160f, 10f, "px")
            addUncappedSlider("Bulge Push Depth", "bulge_depth", 42f, 2f, "px")
            addUncappedSlider("Inward Speed", "press_speed_ms", 75f, 5f, "ms")
            addUncappedSlider("Release Bounce Speed", "release_speed_ms", 180f, 10f, "ms")
        }

        populateSliders()

        btnAutoCalibrate.setOnClickListener {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            val screenH = dm.heightPixels.toFloat()
            prefs.edit()
                .putFloat("vol_up_y", screenH * 0.28f)
                .putFloat("vol_down_y", screenH * 0.38f)
                .putFloat("rocker_length", screenH * 0.16f)
                .putFloat("single_btn_length", screenH * 0.07f)
                .putFloat("bulge_depth", 44f)
                .apply()
            populateSliders()
            notifyEngine()
            Toast.makeText(this, "Auto-calibrated to screen height: ${screenH.toInt()}px", Toast.LENGTH_SHORT).show()
        }

        fun refreshExtraKeyUi() {
            val code = prefs.getInt("extra_button_code", -1)
            val name = prefs.getString("extra_button_name", "Hardware Button")
            tvExtraKeyStatus.text = if (code != -1) "$name (Keycode: $code)" else "No extra button mapped."
            btnRenameExtra.isEnabled = code != -1
            btnDeleteExtra.isEnabled = code != -1
        }
        refreshExtraKeyUi()

        btnMapExtra.setOnClickListener {
            btnMapExtra.text = "Press physical button on device..."
            BezelAccessibilityService.isListeningForKey = true
            BezelAccessibilityService.onKeyDetected = { code ->
                runOnUiThread {
                    prefs.edit().putInt("extra_button_code", code).apply()
                    btnMapExtra.text = "Press To Map Extra Button"
                    refreshExtraKeyUi()
                    notifyEngine()
                    Toast.makeText(this, "Mapped Keycode: $code", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnRenameExtra.setOnClickListener {
            val input = TextInputEditText(this).apply {
                setText(prefs.getString("extra_button_name", "Hardware Button"))
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Rename Hardware Key")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    prefs.edit().putString("extra_button_name", input.text.toString().trim()).apply()
                    refreshExtraKeyUi()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnDeleteExtra.setOnClickListener {
            prefs.edit().remove("extra_button_code").remove("extra_button_name").apply()
            refreshExtraKeyUi()
            notifyEngine()
            Toast.makeText(this, "Extra button mapping deleted", Toast.LENGTH_SHORT).show()
        }
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
        val colors = arrayOf("Black", "Dark Titanium", "Electric Blue", "Crimson Red", "Custom HEX")
        val values = intArrayOf(Color.BLACK, 0xFF3A3A3C.toInt(), 0xFF0A84FF.toInt(), 0xFFFF3B30.toInt(), 0)

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
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFF1E1E1E.toInt())
        }

        val title = TextView(this).apply {
            text = "Draw Your Custom Bezel Contour"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 6)
        }
        val sub = TextView(this).apply {
            text = "Draw a curve from top to bottom. The horizontal distance from the line represents the indentation."
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
                prefs.edit().putString("custom_points_store", data).apply()
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
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val edgeGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF444446.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
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
        val edgeBaseline = 24f

        return points.map {
            val normY = ((it.y - minY) / heightSpan).coerceIn(0f, 1f)
            val normDepth = ((it.x - edgeBaseline) / (width - edgeBaseline)).coerceIn(0f, 1f)
            "$normY,$normDepth"
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
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                path.lineTo(event.x, event.y)
                points.add(PointF(event.x, event.y))
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF161618.toInt())
        canvas.drawLine(24f, 0f, 24f, height.toFloat(), edgeGuidePaint)
        canvas.drawPath(path, paint)
    }
}
