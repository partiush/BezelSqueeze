package com.bezel.squeeze

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
        val switchEnableVolumePanel: MaterialSwitch = findViewById(R.id.switchEnableVolumePanel)
        val boxPanelOptions: LinearLayout = findViewById(R.id.boxPanelOptions)
        val spinnerPanelStyle: Spinner = findViewById(R.id.spinnerPanelStyle)
        val btnOpenCodeEditor: MaterialButton = findViewById(R.id.btnOpenCodeEditor)

        val switchContinuousPress: MaterialSwitch = findViewById(R.id.switchContinuousPress)
        val switchHaptics: MaterialSwitch = findViewById(R.id.switchHaptics)
        val boxButtonBehaviorContainer: LinearLayout = findViewById(R.id.boxButtonBehaviorContainer)
        val boxPanelGeometryContainer: LinearLayout = findViewById(R.id.boxPanelGeometryContainer)
        val boxBulgeContainer: LinearLayout = findViewById(R.id.boxBulgeContainer)

        val spinnerShape: Spinner = findViewById(R.id.spinnerShape)
        val btnOpenDraw: MaterialButton = findViewById(R.id.btnOpenDraw)
        val colorPreview: View = findViewById(R.id.colorPreview)
        val btnPickColor: MaterialButton = findViewById(R.id.btnPickColor)
        val switchRightEdge: MaterialSwitch = findViewById(R.id.switchRightEdge)
        val switchUnified: MaterialSwitch = findViewById(R.id.switchUnified)
        val btnAutoCalibrate: MaterialButton = findViewById(R.id.btnAutoCalibrate)

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

        // Master Volume Panel Toggle
        val panelEnabled = prefs.getBoolean("volume_panel_enabled", true)
        switchEnableVolumePanel.isChecked = panelEnabled
        boxPanelOptions.visibility = if (panelEnabled) View.VISIBLE else View.GONE
        switchEnableVolumePanel.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("volume_panel_enabled", isChecked).apply()
            boxPanelOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
            notifyEngine()
        }

        // Panel Options with Custom Scripted Engine
        val panels = arrayOf(
            "iOS Dynamic Panel",
            "Samsung OneUI",
            "Xiaomi HyperOS",
            "OPPO ColorOS",
            "Google Pixel",
            "Custom Scripted Engine (HTML5/Canvas)"
        )
        spinnerPanelStyle.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, panels)
        val currentStyle = prefs.getInt("volume_panel_style", 0).coerceIn(0, 5)
        spinnerPanelStyle.setSelection(currentStyle)
        btnOpenCodeEditor.visibility = if (currentStyle == 5) View.VISIBLE else View.GONE

        spinnerPanelStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit().putInt("volume_panel_style", pos).apply()
                btnOpenCodeEditor.visibility = if (pos == 5) View.VISIBLE else View.GONE
                notifyEngine()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        btnOpenCodeEditor.setOnClickListener {
            showCodeEditorDialog(::notifyEngine)
        }

        // Continuous Press and Haptics
        switchContinuousPress.isChecked = prefs.getBoolean("enable_continuous_press", true)
        switchContinuousPress.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_continuous_press", isChecked).apply()
            notifyEngine()
        }

        switchHaptics.isChecked = prefs.getBoolean("enable_haptics", true)
        switchHaptics.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_haptics", isChecked).apply()
        }

        // Sliders for Button & Volume Tuning
        fun populateTuningSliders() {
            addSafeSlider(boxButtonBehaviorContainer, "Volume Step Size", "vol_step_size", 1f, 1f, "steps", 1f, 10f, ::notifyEngine)
            addSafeSlider(boxButtonBehaviorContainer, "Hold Repeat Delay", "continuous_speed_ms", 120f, 10f, "ms", 30f, 500f, ::notifyEngine)
            addSafeSlider(boxButtonBehaviorContainer, "Max Allowed Volume Cap", "vol_max_limit_pct", 100f, 5f, "%", 10f, 100f, ::notifyEngine)
        }
        populateTuningSliders()

        // Sliders for Panel Geometry, Scale & Rotation
        fun populateGeometrySliders() {
            addSafeSlider(boxPanelGeometryContainer, "Panel Width", "custom_panel_width", 50f, 2f, "px", 10f, 300f, ::notifyEngine)
            addSafeSlider(boxPanelGeometryContainer, "Panel Height", "custom_panel_height", 240f, 10f, "px", 60f, 800f, ::notifyEngine)
            addSafeSlider(boxPanelGeometryContainer, "Scale Multiplier", "custom_panel_scale", 100f, 5f, "%", 20f, 300f, ::notifyEngine)
            addSafeSlider(boxPanelGeometryContainer, "Rotation Angle", "custom_panel_rotation", 0f, 5f, "°", -180f, 180f, ::notifyEngine)
            addSafeSlider(boxPanelGeometryContainer, "Corner Radius", "custom_panel_corner_radius", 25f, 2f, "px", 0f, 150f, ::notifyEngine)
            addSafeSlider(boxPanelGeometryContainer, "Horizontal X-Offset", "custom_panel_offset_x", 0f, 5f, "px", -300f, 300f, ::notifyEngine)
            addSafeSlider(boxPanelGeometryContainer, "Vertical Y-Offset", "custom_panel_offset_y", 0f, 5f, "px", -500f, 500f, ::notifyEngine)
            addSafeSlider(boxPanelGeometryContainer, "Auto-Dismiss Timeout", "panel_dismiss_timeout_ms", 2200f, 100f, "ms", 500f, 10000f, ::notifyEngine)
        }
        populateGeometrySliders()

        // Bezel Bulge Setup
        val shapes = arrayOf("Smooth Squircle", "Organic Teardrop", "Geometric Pill", "Beveled Wedge", "Freehand Drawn Curve")
        spinnerShape.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, shapes)
        val currentShape = prefs.getInt("shape_type", 0).coerceIn(0, 4)
        spinnerShape.setSelection(currentShape)
        btnOpenDraw.visibility = if (currentShape == 4) View.VISIBLE else View.GONE
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

        fun populateBulgeSliders() {
            addSafeSlider(boxBulgeContainer, "Volume Up Height", "vol_up_y", 720f, 10f, "px", 100f, 3000f, ::notifyEngine)
            addSafeSlider(boxBulgeContainer, "Volume Down Height", "vol_down_y", 960f, 10f, "px", 100f, 3000f, ::notifyEngine)
            addSafeSlider(boxBulgeContainer, "Rocker Total Length", "rocker_length", 380f, 10f, "px", 50f, 1000f, ::notifyEngine)
            addSafeSlider(boxBulgeContainer, "Separate Button Length", "single_btn_length", 160f, 10f, "px", 30f, 600f, ::notifyEngine)
            addSafeSlider(boxBulgeContainer, "Bulge Push Depth", "bulge_depth", 42f, 2f, "px", 5f, 200f, ::notifyEngine)
            addSafeSlider(boxBulgeContainer, "Inward Speed", "press_speed_ms", 75f, 5f, "ms", 10f, 500f, ::notifyEngine)
            addSafeSlider(boxBulgeContainer, "Release Bounce Speed", "release_speed_ms", 180f, 10f, "ms", 30f, 1000f, ::notifyEngine)
        }
        populateBulgeSliders()

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
            notifyEngine()
            Toast.makeText(this, "Calibrated to display height: ${screenH.toInt()}px", Toast.LENGTH_SHORT).show()
        }

        fun refreshExtraKeyUi() {
            val code = prefs.getInt("extra_button_code", -1)
            val name = prefs.getString("extra_button_name", "Hardware Button")
            tvExtraKeyStatus.text = if (code != -1) "$name (Keycode:$code)" else "No extra button mapped."
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

    private fun addSafeSlider(
        container: LinearLayout,
        label: String,
        key: String,
        defVal: Float,
        step: Float,
        unit: String,
        minBound: Float,
        maxBound: Float,
        notifyEngine: () -> Unit
    ) {
        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val currentVal = prefs.getFloat(key, defVal).coerceIn(minBound, maxBound)
        val titleTv = TextView(this).apply {
            text = "$label: ${currentVal.toInt()}$unit"
            textSize = 14f
            setTextColor(0xFFCCCCCC.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val editBtn = Button(this).apply {
            text = "Edit"
            textSize = 12f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(0xFF0A84FF.toInt())
        }

        titleRow.addView(titleTv)
        titleRow.addView(editBtn)
        row.addView(titleRow)

        val safeStep = if (step > 0f) step else 1f
        val slider = Slider(this).apply {
            valueFrom = minBound
            valueTo = maxBound
            stepSize = safeStep
            value = currentVal
            setLabelFormatter { "${it.toInt()}$unit" }
        }

        fun updateValue(newVal: Float) {
            val clamped = newVal.coerceIn(minBound, maxBound)
            prefs.edit().putFloat(key, clamped).apply()
            titleTv.text = "$label: ${clamped.toInt()}$unit"
            if (clamped in slider.valueFrom..slider.valueTo) {
                slider.value = clamped
            }
            notifyEngine()
        }

        editBtn.setOnClickListener {
            val textLayout = TextInputLayout(this).apply { setPadding(40, 16, 40, 0) }
            val input = TextInputEditText(textLayout.context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
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

        val minusBtn = Button(this).apply {
            text = "-${safeStep.toInt()}"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { updateValue(prefs.getFloat(key, defVal) - safeStep) }
        }

        val plusBtn = Button(this).apply {
            text = "+${safeStep.toInt()}"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { updateValue(prefs.getFloat(key, defVal) + safeStep) }
        }

        stepRow.addView(minusBtn)
        stepRow.addView(plusBtn)

        row.addView(slider)
        row.addView(stepRow)
        container.addView(row)
    }

    private fun showCodeEditorDialog(onSaved: () -> Unit) {
        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val defaultCode = """
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: transparent; }
    #container {
      width: 100%; height: 100%;
      display: flex; flex-direction: column; justify-content: flex-end;
      align-items: center; box-sizing: border-box;
      border-radius: 28px; background: rgba(22, 22, 26, 0.85);
      border: 1.5px solid rgba(255, 255, 255, 0.15);
      box-shadow: 0 12px 36px rgba(0,0,0,0.6);
      backdrop-filter: blur(20px); overflow: hidden; position: relative;
    }
    #bar {
      width: 100%; background: linear-gradient(180deg, #0A84FF, #0051FF);
      height: 50%; transition: height 0.08s ease-out;
      border-radius: 0 0 26px 26px;
      box-shadow: 0 0 16px rgba(10, 132, 255, 0.6);
    }
    #percent {
      position: absolute; top: 18px; width: 100%; text-align: center;
      color: #FFFFFF; font-family: sans-serif; font-weight: 700;
      font-size: 15px; text-shadow: 0 2px 4px rgba(0,0,0,0.6);
    }
    #icon {
      position: absolute; bottom: 18px; width: 100%; text-align: center;
      color: #FFFFFF; font-size: 18px;
    }
  </style>
</head>
<body>
  <div id="container">
    <div id="percent">50%</div>
    <div id="bar"></div>
    <div id="icon">🔊</div>
  </div>
  <script>
    // Called live by the Android Accessibility engine at 60fps or upon button press:
    function onVolumeChanged(volume, isMuted, rawLevel, maxLevel) {
      const pct = Math.round(volume * 100);
      document.getElementById('percent').innerText = pct + '%';
      document.getElementById('bar').style.height = pct + '%';
      document.getElementById('icon').innerText = isMuted || pct === 0 ? '🔇' : (pct > 60 ? '🔊' : '🔉');
    }
  </script>
</body>
</html>
""".trimIndent()

        val savedCode = prefs.getString("custom_panel_html_code", defaultCode) ?: defaultCode
        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFF161618.toInt())
        }

        val title = TextView(this).apply {
            text = "Custom Panel Live Code Engine"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 4)
        }
        val sub = TextView(this).apply {
            text = "Inject full HTML5, CSS3, Canvas, WebGL, or JavaScript. The function onVolumeChanged(volume, isMuted, raw, max) is automatically invoked."
            textSize = 12f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 0, 0, 12)
        }
        root.addView(title)
        root.addView(sub)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 360).toInt()
            )
        }

        val codeInput = EditText(this).apply {
            setText(savedCode)
            setTextColor(0xFF58A6FF.toInt())
            setBackgroundColor(0xFF0D1117.toInt())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(16, 16, 16, 16)
            isVerticalScrollBarEnabled = true
        }
        scrollView.addView(codeInput)
        root.addView(scrollView)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }
        val resetBtn = Button(this).apply {
            text = "Default Template"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(0xFFFF453A.toInt())
            setOnClickListener {
                codeInput.setText(defaultCode)
            }
        }
        val saveBtn = MaterialButton(this).apply {
            text = "Save & Compile"
            setOnClickListener {
                prefs.edit().putString("custom_panel_html_code", codeInput.text.toString()).apply()
                onSaved()
                dialog.dismiss()
                Toast.makeText(this@MainActivity, "Custom Code Saved & Applied", Toast.LENGTH_SHORT).show()
            }
        }
        btnRow.addView(resetBtn)
        btnRow.addView(saveBtn)
        root.addView(btnRow)

        dialog.setContentView(root)
        dialog.show()
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
        val clearBtn = Button(this).apply {
            text = "Clear"
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
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
