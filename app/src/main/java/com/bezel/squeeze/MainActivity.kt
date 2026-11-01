package com.bezel.squeeze

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.AlertDialog
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
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var permCard: MaterialCardView
    private lateinit var batteryCard: MaterialCardView
    private lateinit var statusBadge: TextView
    private lateinit var rockerSliderBox: LinearLayout
    private lateinit var splitSliderBox: LinearLayout
    private lateinit var customDrawBtn: Button

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

        // --- Accessibility Card ---
        permCard = MaterialCardView(this).apply {
            radius = 24f
            setCardBackgroundColor(0xFF1E1E1E.toInt())
            val inner = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                val tip = TextView(this@MainActivity).apply {
                    text = "Permission required to detect your button clicks."
                    textSize = 14f
                    setTextColor(0xFFFF9F0A.toInt())
                    setPadding(0, 0, 0, 16)
                }
                val btn = Button(this@MainActivity).apply {
                    text = "Enable in Settings"
                    setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
                addView(tip)
                addView(btn)
            }
            addView(inner)
        }
        mainLayout.addView(permCard)

        // --- Background Protection Card ---
        batteryCard = MaterialCardView(this).apply {
            radius = 24f
            setCardBackgroundColor(0xFF1E1E1E.toInt())
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            layoutParams = params

            val inner = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                val tip = TextView(this@MainActivity).apply {
                    text = "Prevent Android from stopping the app when cleared from background."
                    textSize = 14f
                    setTextColor(0xFFCCCCCC.toInt())
                    setPadding(0, 0, 0, 12)
                }
                val btn = Button(this@MainActivity).apply {
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

        // --- On-Screen Test Card ---
        addCard(mainLayout, "Live Screen Test") { container ->
            val tip = TextView(this).apply {
                text = "Hold the button below to see the effect directly on your screen bezel."
                textSize = 13f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, 0, 0, 12)
            }
            container.addView(tip)

            val holdBtn = Button(this).apply {
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
        addCard(mainLayout, "Shape & Color") { container ->
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

            customDrawBtn = Button(this).apply {
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
                setPadding(0, 16, 0, 8)
            }
            val previewBox = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(70, 70)
                setBackgroundColor(prefs.getInt("bulge_color", Color.BLACK))
            }
            val chooseColBtn = Button(this).apply {
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

        // --- 2. Button Arrangement ---
        addCard(mainLayout, "Button Layout") { container ->
            val edgeSwitch = Switch(this).apply {
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

            val rockerSwitch = Switch(this).apply {
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

            addFineSlider(rockerSliderBox, "Total Bar Length", "rocker_length", 380f, 10f, "px", ::notifyEngine)
            container.addView(rockerSliderBox)

            addFineSlider(splitSliderBox, "Separate Button Length", "single_btn_length", 160f, 10f, "px", ::notifyEngine)
            container.addView(splitSliderBox)

            val isRocker = prefs.getBoolean("is_unified_rocker", true)
            rockerSliderBox.visibility = if (isRocker) View.VISIBLE else View.GONE
            splitSliderBox.visibility = if (isRocker) View.GONE else View.VISIBLE
        }

        // --- 3. Placement & Sizing ---
        addCard(mainLayout, "Position & Size") { container ->
            addFineSlider(container, "Volume Up Height", "vol_up_y", 720f, 10f, "px", ::notifyEngine)
            addFineSlider(container, "Volume Down Height", "vol_down_y", 960f, 10f, "px", ::notifyEngine)
            addFineSlider(container, "Bulge Depth", "bulge_depth", 40f, 2f, "px", ::notifyEngine)
            addFineSlider(container, "Inward Speed", "press_speed_ms", 85f, 5f, "ms", ::notifyEngine)
            addFineSlider(container, "Release Speed", "release_speed_ms", 160f, 10f, "ms", ::notifyEngine)
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

            val detectBtn = Button(this).apply {
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

            addFineSlider(container, "Extra Button Height", "extra_y", 500f, 10f, "px", ::notifyEngine)
            addFineSlider(container, "Extra Button Length", "extra_length", 160f, 10f, "px", ::notifyEngine)
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
            text = "$label: ${currentVal.toInt()} $unit"
            textSize = 14f
            setTextColor(0xFFCCCCCC.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val editBtn = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Edit"
            textSize = 12f
            setOnClickListener {
                val input = EditText(this@MainActivity).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText(prefs.getFloat(key, def).toString())
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Set $label")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        input.text.toString().toFloatOrNull()?.let { entered ->
                            prefs.edit().putFloat(key, entered).apply()
                            titleTv.text = "$label: ${entered.toInt()} $unit"
                            onChanged()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        headerRow.addView(titleTv)
        headerRow.addView(editBtn)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 16)
        }

        fun adjust(delta: Float) {
            val newVal = (prefs.getFloat(key, def) + delta).coerceAtLeast(0f)
            prefs.edit().putFloat(key, newVal).apply()
            titleTv.text = "$label: ${newVal.toInt()} $unit"
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

    private fun showColorDialog(onColorChosen: (Int) -> Unit) {
        val colors = arrayOf("Black", "Dark Gray", "Blue", "Red", "Custom HEX")
        val values = intArrayOf(Color.BLACK, 0xFF333333.toInt(), 0xFF007AFF.toInt(), 0xFFFF3B30.toInt(), 0)

        AlertDialog.Builder(this)
            .setTitle("Select Bezel Color")
            .setItems(colors) { _, which ->
                if (which == 4) {
                    val input = EditText(this).apply { hint = "#000000" }
                    AlertDialog.Builder(this)
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
            text = "Draw a curve from top to bottom representing how the bezel should bend inward."
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
            setOnClickListener { drawCanvas.reset() }
        }
        val saveBtn = Button(this).apply {
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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF007AFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    fun reset() {
        points.clear()
        invalidate()
    }

    fun getNormalizedData(): String {
        if (points.size < 3) return ""
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
        canvas.drawColor(0xFF111111.toInt())
        if (points.size > 1) {
            for (i in 0 until points.size - 1) {
                canvas.drawLine(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, paint)
            }
        }
    }
}
