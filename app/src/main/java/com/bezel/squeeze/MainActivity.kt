package com.bezel.squeeze

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
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

    private lateinit var permCard: MaterialCardView
    private lateinit var batteryCard: MaterialCardView
    private lateinit var statusBadge: TextView
    private lateinit var rockerSliderBox: LinearLayout
    private lateinit var splitSliderBox: LinearLayout
    private lateinit var customDrawBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)

        val root = ScrollView(this).apply { setPadding(32, 40, 32, 60) }
        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(mainLayout)

        fun notifyEngine() {
            sendBroadcast(Intent("com.bezel.squeeze.UPDATE_SETTINGS").setPackage(packageName))
        }

        // --- Status Badge ---
        statusBadge = TextView(this).apply {
            text = "● Service Active"
            textSize = 15f
            setTextColor(0xFF34C759.toInt())
            setPadding(0, 0, 0, 16)
        }
        mainLayout.addView(statusBadge)

        // --- Permission Banner ---
        permCard = MaterialCardView(this).apply {
            radius = 28f
            setCardBackgroundColor(0xFF1E1E1E.toInt())
            val inner = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                val tip = TextView(this@MainActivity).apply {
                    text = "Permission required to track button presses."
                    textSize = 14f
                    setTextColor(0xFFFF9F0A.toInt())
                    setPadding(0, 0, 0, 16)
                }
                val btn = MaterialButton(this@MainActivity).apply {
                    text = "Enable in Accessibility"
                    setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
                addView(tip)
                addView(btn)
            }
            addView(inner)
        }
        mainLayout.addView(permCard)

        // --- Battery Protection ---
        batteryCard = MaterialCardView(this).apply {
            radius = 28f
            setCardBackgroundColor(0xFF1E1E1E.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }

            val inner = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                val tip = TextView(this@MainActivity).apply {
                    text = "Prevent Android from stopping the engine in the background."
                    textSize = 14f
                    setTextColor(0xFFCCCCCC.toInt())
                    setPadding(0, 0, 0, 12)
                }
                val btn = MaterialButton(this@MainActivity).apply {
                    text = "Allow Continuous Background Run"
                    setOnClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            try { startActivity(intent) } catch (_: Exception) {}
                        }
                    }
                }
                addView(tip)
                addView(btn)
            }
            addView(inner)
        }
        mainLayout.addView(batteryCard)

        // --- Live Screen Test ---
        addCard(mainLayout, "Live Screen Test") { container ->
            val tip = TextView(this).apply {
                text = "Hold down the button below to preview the bulge directly on your screen bezel."
                textSize = 13f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, 0, 0, 12)
            }
            container.addView(tip)

            val holdBtn = MaterialButton(this).apply {
                text = "Hold to Test Bezel Squeeze"
                setOnTouchListener { _, event ->
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
            }
            container.addView(holdBtn)
        }

        // --- 1. Shape & Appearance ---
        addCard(mainLayout, "Shape & Appearance") { container ->
            val spinner = Spinner(this)
            val shapes = arrayOf(
                "Smooth Curved Lip",
                "Organic Teardrop",
                "Rounded Pill",
                "Beveled Edge",
                "Custom Freehand Drawing"
            )
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, shapes)
            spinner.setSelection(prefs.getInt("shape_type", 0))

            customDrawBtn = MaterialButton(this).apply {
                text = "Open Drawing Canvas"
                visibility = if (prefs.getInt("shape_type", 0) == 4) View.VISIBLE else View.GONE
                setOnClickListener { showDrawingDialog(::notifyEngine) }
            }

            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    prefs.edit().putInt("shape_type", pos).apply()
                    customDrawBtn.visibility = if (pos == 4) View.VISIBLE else View.GONE
                    notifyEngine()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
            container.addView(spinner)
            container.addView(customDrawBtn)

            // Color Selector
            val colRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 20, 0, 8)
            }
            val previewBox = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(72, 72)
                setBackgroundColor(prefs.getInt("bulge_color", Color.BLACK))
            }
            val chooseColBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Change Bezel Color"
                setOnClickListener {
                    showColorDialog { newColor ->
                        prefs.edit().putInt("bulge_color", newColor).apply()
                        previewBox.setBackgroundColor(newColor)
                        notifyEngine()
                    }
                }
            }
            colRow.addView(previewBox)
            colRow.addView(chooseColBtn)
            container.addView(colRow)
        }

        // --- 2. Button Placement & Layout ---
        addCard(mainLayout, "Button Layout") { container ->
            val edgeSwitch = MaterialSwitch(this).apply {
                text = "Buttons are on the Right Edge (Off = Left)"
                isChecked = prefs.getBoolean("is_right_edge", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("is_right_edge", isChecked).apply()
                    notifyEngine()
                }
            }
            container.addView(edgeSwitch)

            rockerSliderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            splitSliderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

            val rockerSwitch = MaterialSwitch(this).apply {
                text = "Unified Single Volume Bar"
                isChecked = prefs.getBoolean("is_unified_rocker", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("is_unified_rocker", isChecked).apply()
                    rockerSliderBox.visibility = if (isChecked) View.VISIBLE else View.GONE
                    splitSliderBox.visibility = if (isChecked) View.GONE else View.VISIBLE
                    notifyEngine()
                }
            }
            container.addView(rockerSwitch)

            addMaterialSlider(rockerSliderBox, "Total Bar Length", "rocker_length", 80f, 900f, 380f, 10f, "px", ::notifyEngine)
            container.addView(rockerSliderBox)

            addMaterialSlider(splitSliderBox, "Separate Button Length", "single_btn_length", 50f, 500f, 160f, 10f, "px", ::notifyEngine)
            container.addView(splitSliderBox)

            val isRocker = prefs.getBoolean("is_unified_rocker", true)
            rockerSliderBox.visibility = if (isRocker) View.VISIBLE else View.GONE
            splitSliderBox.visibility = if (isRocker) View.GONE else View.VISIBLE
        }

        // --- 3. Placement & Sizing ---
        addCard(mainLayout, "Position & Size") { container ->
            addMaterialSlider(container, "Volume Up Height", "vol_up_y", 50f, 2500f, 720f, 10f, "px", ::notifyEngine)
            addMaterialSlider(container, "Volume Down Height", "vol_down_y", 50f, 2500f, 960f, 10f, "px", ::notifyEngine)
            addMaterialSlider(container, "Bulge Depth", "bulge_depth", 5f, 250f, 40f, 2f, "px", ::notifyEngine)
            addMaterialSlider(container, "Inward Speed", "press_speed_ms", 20f, 400f, 85f, 5f, "ms", ::notifyEngine)
            addMaterialSlider(container, "Release Speed", "release_speed_ms", 40f, 600f, 160f, 10f, "ms", ::notifyEngine)
        }

        // --- 4. Extra Physical Button ---
        addCard(mainLayout, "Extra Hardware Button") { container ->
            val mappedCode = prefs.getInt("extra_button_code", -1)
            val infoTv = TextView(this).apply {
                text = if (mappedCode != -1) "Mapped Key Code: $mappedCode" else "No extra button mapped."
                textSize = 14f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, 0, 0, 10)
            }
            container.addView(infoTv)

            val detectBtn = MaterialButton(this).apply {
                text = "Map Extra Button"
                setOnClickListener {
                    text = "Press your button now..."
                    BezelAccessibilityService.isListeningForKey = true
                    BezelAccessibilityService.onKeyDetected = { code ->
                        runOnUiThread {
                            prefs.edit().putInt("extra_button_code", code).apply()
                            infoTv.text = "Mapped Key Code: $code"
                            text = "Map Extra Button"
                            notifyEngine()
                            Toast.makeText(this@MainActivity, "Saved Button: $code", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            container.addView(detectBtn)

            addMaterialSlider(container, "Extra Button Height", "extra_y", 50f, 2500f, 500f, 10f, "px", ::notifyEngine)
            addMaterialSlider(container, "Extra Button Length", "extra_length", 50f, 600f, 160f, 10f, "px", ::notifyEngine)
        }

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun addCard(parent: LinearLayout, title: String, content: (LinearLayout) -> Unit) {
        val card = MaterialCardView(this).apply {
            radius = 28f
            setCardBackgroundColor(0xFF161618.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }

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

    private fun addMaterialSlider(
        parent: LinearLayout,
        label: String,
        key: String,
        min: Float,
        max: Float,
        def: Float,
        step: Float,
        unit: String,
        onChanged: () -> Unit
    ) {
        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val currentVal = prefs.getFloat(key, def).coerceIn(min, max)

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

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

        headerRow.addView(titleTv)
        headerRow.addView(editBtn)
        parent.addView(headerRow)

        val slider = Slider(this).apply {
            valueFrom = min
            valueTo = max
            stepSize = step
            value = currentVal
            setLabelFormatter { "${it.toInt()} $unit" }
        }

        editBtn.setOnClickListener {
            val textInputLayout = TextInputLayout(this@MainActivity).apply {
                setPadding(40, 16, 40, 0)
            }
            val input = TextInputEditText(textInputLayout.context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(slider.value.toInt().toString())
            }
            textInputLayout.addView(input)

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Set $label")
                .setView(textInputLayout)
                .setPositiveButton("Save") { _, _ ->
                    input.text.toString().toFloatOrNull()?.let { entered ->
                        val clamped = entered.coerceIn(min, max)
                        slider.value = clamped
                        prefs.edit().putFloat(key, clamped).apply()
                        titleTv.text = "$label: ${clamped.toInt()} $unit"
                        onChanged()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.edit().putFloat(key, value).apply()
                titleTv.text = "$label: ${value.toInt()} $unit"
                onChanged()
            }
        }

        parent.addView(slider)
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

    private fun updatePermissionStatus() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledList = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isRunning = enabledList.any { it.resolveInfo.serviceInfo.packageName == packageName }

        permCard.visibility = if (isRunning) View.GONE else View.VISIBLE
        statusBadge.visibility = if (isRunning) View.VISIBLE else View.GONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            batteryCard.visibility = if (pm.isIgnoringBatteryOptimizations(packageName)) View.GONE else View.VISIBLE
        }
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
