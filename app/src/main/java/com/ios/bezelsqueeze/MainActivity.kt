package com.ios.bezelsqueeze

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var permContainer: LinearLayout
    private lateinit var statusBadge: TextView
    private lateinit var rockerSliderBox: LinearLayout
    private lateinit var splitSliderBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()

        val root = ScrollView(this).apply { setPadding(44, 48, 44, 64) }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(container)

        fun notifyService() {
            sendBroadcast(Intent("com.ios.bezelsqueeze.UPDATE_SETTINGS").setPackage(packageName))
        }

        // --- Status & Permission Header ---
        statusBadge = TextView(this).apply {
            text = "● iOS Squeeze Active"
            textSize = 15f
            setTextColor(0xFF34C759.toInt()) // iOS System Green
            setPadding(0, 0, 0, 16)
        }
        container.addView(statusBadge)

        permContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val tip = TextView(this@MainActivity).apply {
                text = "Accessibility service is currently off. Turn it on to track physical volume clicks."
                textSize = 14f
                setTextColor(0xFFFF9500.toInt()) // iOS Orange
                setPadding(0, 0, 0, 12)
            }
            val btn = Button(this@MainActivity).apply {
                text = "Enable Accessibility Engine"
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            addView(tip)
            addView(btn)
            setPadding(0, 0, 0, 24)
        }
        container.addView(permContainer)

        fun addTitle(text: String) {
            container.addView(TextView(this).apply {
                this.text = text
                textSize = 17f
                setPadding(0, 32, 0, 10)
            })
        }

        fun addSlider(parent: LinearLayout, label: String, key: String, min: Float, max: Float, def: Float) {
            val currentVal = prefs.getFloat(key, def)
            val tv = TextView(this).apply { text = "$label: ${currentVal.toInt()} px" }
            val seek = SeekBar(this).apply {
                this.max = (max - min).toInt()
                progress = (currentVal - min).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        val v = min + p
                        tv.text = "$label: ${v.toInt()} px"
                        prefs.edit().putFloat(key, v).apply()
                        notifyService()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            parent.addView(tv)
            parent.addView(seek)
        }

        // --- 1. Edge & Rocker Architecture ---
        addTitle("Edge & Rocker Layout")

        val edgeSwitch = Switch(this).apply {
            text = "Buttons on Right Edge (Off = Left)"
            isChecked = prefs.getBoolean("is_right_side", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("is_right_side", isChecked).apply()
                notifyService()
            }
        }
        container.addView(edgeSwitch)

        rockerSliderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        splitSliderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val rockerSwitch = Switch(this).apply {
            text = "Unified Single Rocker Bar (iOS Style)"
            isChecked = prefs.getBoolean("unified_rocker", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("unified_rocker", isChecked).apply()
                rockerSliderBox.visibility = if (isChecked) View.VISIBLE else View.GONE
                splitSliderBox.visibility = if (isChecked) View.GONE else View.VISIBLE
                notifyService()
            }
        }
        container.addView(rockerSwitch)

        // --- 2. Shape Selection ---
        addTitle("Bezel Bulge Shape")
        val spinner = Spinner(this)
        val shapes = arrayOf("iOS 18 Squircle (Flat Lip, Natural)", "Smooth Organic Spline", "Oval / Pill")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, shapes)
        spinner.setSelection(prefs.getInt("shape_profile", 0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                prefs.edit().putInt("shape_profile", pos).apply()
                notifyService()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        container.addView(spinner)

        // --- 3. Calibration Sliders ---
        addTitle("Button Position & Depth")
        addSlider(container, "Volume Up Peak Y", "vol_up_y", 100f, screenHeight, 720f)
        addSlider(container, "Volume Down Peak Y", "vol_down_y", 100f, screenHeight, 920f)
        addSlider(container, "Inward Push Depth", "bulge_depth", 10f, 90f, 36f)

        // Unified Rocker Sliders
        addSlider(rockerSliderBox, "Total Rocker Bar Length", "rocker_total_length", 160f, 550f, 320f)
        container.addView(rockerSliderBox)

        // Split Button Sliders
        addSlider(splitSliderBox, "Individual Button Length", "single_btn_length", 80f, 300f, 140f)
        container.addView(splitSliderBox)

        // Set initial visibility
        val isRocker = prefs.getBoolean("unified_rocker", true)
        rockerSliderBox.visibility = if (isRocker) View.VISIBLE else View.GONE
        splitSliderBox.visibility = if (isRocker) View.GONE else View.VISIBLE

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
    }

    private fun updatePermissionUi() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isRunning = enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }

        if (isRunning) {
            permContainer.visibility = View.GONE
            statusBadge.visibility = View.VISIBLE
            statusBadge.text = "● iOS Squeeze Engine Active"
            statusBadge.setTextColor(0xFF34C759.toInt())
        } else {
            permContainer.visibility = View.VISIBLE
            statusBadge.visibility = View.GONE
        }
    }
}
