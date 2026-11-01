package com.bezel.squeeze

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
    private lateinit var customShapeControlsBox: LinearLayout
    private lateinit var extraKeyCard: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)

        val root = ScrollView(this).apply { setPadding(32, 40, 32, 60) }
        val mainLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(mainLayout)

        fun notifyEngine() {
            sendBroadcast(Intent("com.bezel.squeeze.UPDATE_SETTINGS").setPackage(packageName))
        }

        // --- Status Badge & Auto-Hiding Permission Card ---
        statusBadge = TextView(this).apply {
            text = "● Bezel Engine Active (Zero Latency)"
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
                    text = "Accessibility service is disabled. Enable it to capture button presses."
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

        // --- Live Screen Interactive Test Bar ---
        addCard(mainLayout, "Live Calibration & Edge Alignment") { container ->
            val desc = TextView(this).apply {
                text = "Hold down the button below to preview the bulge live on your screen edge."
                textSize = 13f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, 0, 0, 12)
            }
            container.addView(desc)

            val holdTestBtn = Button(this).apply {
                text = "Hold to Squeeze (Live Edge Test)"
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            sendBroadcast(Intent("com.bezel.squeeze.TRIGGER_TEST").setPackage(packageName))
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            sendBroadcast(Intent("com.bezel.squeeze.RELEASE_TEST").setPackage(packageName))
                            true
                        }
                        else -> false
                    }
                }
            }
            container.addView(holdTestBtn)
        }

        // --- 1. Colors & Custom Shapes ---
        addCard(mainLayout, "Style, Colors & Shape Geometry") { container ->
            val edgeSwitch = Switch(this).apply {
                text = "Buttons on Right Edge (Off = Left Edge)"
                isChecked = prefs.getBoolean("is_right_side", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("is_right_side", isChecked).apply()
                    notifyEngine()
                }
            }
            container.addView(edgeSwitch)

            val spinner = Spinner(this)
            val shapes = arrayOf(
                "Authentic Squircle (Apple Contour)",
                "Organic Teardrop",
                "Geometric Pill",
                "Beveled Wedge",
                "Custom Dynamic Shape Pop-Out"
            )
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, shapes)
            spinner.setSelection(prefs.getInt("shape_profile", 0))

            customShapeControlsBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (prefs.getInt("shape_profile", 0) == 4) View.VISIBLE else View.GONE
            }

            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    prefs.edit().putInt("shape_profile", pos).apply()
                    customShapeControlsBox.visibility = if (pos == 4) View.VISIBLE else View.GONE
                    notifyEngine()
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
            container.addView(spinner)

            // Setup Custom Shape Controls
            setupCustomShapePanel(customShapeControlsBox, ::notifyEngine)
            container.addView(customShapeControlsBox)

            // Fill Color Selector
            val colRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 16, 0, 8)
            }
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(80, 80)
                setBackgroundColor(prefs.getInt("bulge_color", Color.BLACK))
            }
            val pickColBtn = Button(this).apply {
                text = "Pick Custom Bulge HEX Color"
                setOnClickListener {
                    showHexInputDialog("Bulge Fill HEX", prefs.getInt("bulge_color", Color.BLACK)) { col ->
                        prefs.edit().putInt("bulge_color", col).apply()
                        colorView.setBackgroundColor(col)
                        notifyEngine()
                    }
                }
            }
            colRow.addView(colorView)
            colRow.addView(pickColBtn)
            container.addView(colRow)
        }

        // --- 2. Hardware Button Architecture ---
        addCard(mainLayout, "Volume Key Architecture") { container ->
            rockerSliderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            splitSliderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

            val rockerSwitch = Switch(this).apply {
                text = "Unified Single Rocker Key"
                isChecked = prefs.getBoolean("unified_rocker", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("unified_rocker", isChecked).apply()
                    rockerSliderBox.visibility = if (isChecked) View.VISIBLE else View.GONE
                    splitSliderBox.visibility = if (isChecked) View.GONE else View.VISIBLE
                    notifyEngine()
                }
            }
            container.addView(rockerSwitch)

            addFineSlider(rockerSliderBox, "Total Rocker Bar Length", "rocker_total_length", 380f, 10f, "px", ::notifyEngine)
            container.addView(rockerSliderBox)

            addFineSlider(splitSliderBox, "Discrete Button Length", "single_btn_length", 160f, 10f, "px", ::notifyEngine)
            container.addView(splitSliderBox)

            val isRocker = prefs.getBoolean("unified_rocker", true)
            rockerSliderBox.visibility = if (isRocker) View.VISIBLE else View.GONE
            splitSliderBox.visibility = if (isRocker) View.GONE else View.VISIBLE
        }

        // --- 3. Dynamic Positions & Sizing ---
        addCard(mainLayout, "Uncapped Dimension Calibration") { container ->
            addFineSlider(container, "Volume Up Peak Y", "vol_up_y", 720f, 10f, "px", ::notifyEngine)
            addFineSlider(container, "Volume Down Peak Y", "vol_down_y", 960f, 10f, "px", ::notifyEngine)
            addFineSlider(container, "Bulge Depth (Inward Push)", "bulge_depth", 42f, 2f, "px", ::notifyEngine)
            addFineSlider(container, "Attack Time (Inward Duration)", "press_speed_ms", 90f, 10f, "ms", ::notifyEngine)
            addFineSlider(container, "Snap-Back Duration", "release_speed_ms", 180f, 10f, "ms", ::notifyEngine)
        }

        // --- 4. Extra / Custom Hardware Keys Learner ---
        extraKeyCard = MaterialCardView(this).apply {
            radius = 24f
            setCardBackgroundColor(0xFF161618.toInt())
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            layoutParams = params

            val inner = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 28, 28, 28)
                val head = TextView(this@MainActivity).apply {
                    text = "Extra Hardware Button Mapping"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(0, 0, 0, 8)
                }
                addView(head)

                val mappedCode = prefs.getInt("extra_key_code_1", -1)
                val statusTv = TextView(this@MainActivity).apply {
                    text = if (mappedCode != -1) "Mapped Key Code: $mappedCode" else "No extra key mapped."
                    textSize = 14f
                    setTextColor(0xFFAAAAAA.toInt())
                    setPadding(0, 0, 0, 12)
                }
                addView(statusTv)

                val learnBtn = Button(this@MainActivity).apply {
                    text = "Press To Map Any Physical Button"
                    setOnClickListener {
                        text = "Press physical button on phone now..."
                        BezelAccessibilityService.isCapturingExtraKey = true
                        BezelAccessibilityService.onKeyCapturedCallback = { code ->
                            runOnUiThread {
                                prefs.edit().putInt("extra_key_code_1", code).apply()
                                statusTv.text = "Mapped Key Code: $code"
                                text = "Press To Map Any Physical Button"
                                notifyEngine()
                                Toast.makeText(this@MainActivity, "Mapped Button Keycode $code", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                addView(learnBtn)

                addFineSlider(this, "Extra Button Peak Y", "extra_btn_y", 500f, 10f, "px", ::notifyEngine)
                addFineSlider(this, "Extra Button Length", "extra_btn_length", 160f, 10f, "px", ::notifyEngine)
            }
            addView(inner)
        }
        mainLayout.addView(extraKeyCard)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupCustomShapePanel(parent: LinearLayout, onUpdate: () -> Unit) {
        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)

        parent.addView(TextView(this).apply {
            text = "Select Dynamic Vector Profile:"
            textSize = 14f
            setPadding(0, 16, 0, 4)
        })

        val shapeSpinner = Spinner(this)
        val types = arrayOf("Circle / Sphere", "Squircle Badge", "10-Point Burst / Star")
        shapeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        shapeSpinner.setSelection(prefs.getInt("custom_geometric_type", 0))
        shapeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit().putInt("custom_geometric_type", pos).apply()
                onUpdate()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        parent.addView(shapeSpinner)

        val strokeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 4)
        }
        val strokeView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(60, 60)
            setBackgroundColor(prefs.getInt("custom_stroke_color", Color.WHITE))
        }
        val strokeBtn = Button(this).apply {
            text = "Pick Outline Stroke Color"
            setOnClickListener {
                showHexInputDialog("Stroke Outline HEX", prefs.getInt("custom_stroke_color", Color.WHITE)) { col ->
                    prefs.edit().putInt("custom_stroke_color", col).apply()
                    strokeView.setBackgroundColor(col)
                    onUpdate()
                }
            }
        }
        strokeRow.addView(strokeView)
        strokeRow.addView(strokeBtn)
        parent.addView(strokeRow)

        addFineSlider(parent, "Zoom / Scale Factor", "custom_shape_scale", 1.0f, 0.1f, "x", onUpdate)
        addFineSlider(parent, "Rotation Angle", "custom_shape_rotation", 0f, 15f, "deg", onUpdate)
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
            text = "$label: ${String.format("%.1f", currentVal)} $unit"
            textSize = 14f
            setTextColor(0xFFCCCCCC.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val directEditBtn = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
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
                            titleTv.text = "$label: ${String.format("%.1f", entered)} $unit"
                            onChanged()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
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

    private fun showHexInputDialog(title: String, current: Int, onColorChosen: (Int) -> Unit) {
        val input = EditText(this).apply {
            hint = String.format("#%06X", 0xFFFFFF and current)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle(title)
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
}
