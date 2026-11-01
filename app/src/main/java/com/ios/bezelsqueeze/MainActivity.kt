package com.ios.bezelsqueeze

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.dynamicanimation.animation.SpringForce

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("bezel_prefs", Context.MODE_PRIVATE)
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()

        val root = ScrollView(this).apply { setPadding(48, 64, 48, 64) }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(container)

        fun addHeader(title: String) {
            container.addView(TextView(this).apply {
                text = title
                textSize = 18f
                setPadding(0, 36, 0, 12)
            })
        }

        fun addSlider(label: String, key: String, min: Float, max: Float, def: Float) {
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
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            container.addView(tv)
            container.addView(seek)
        }

        addHeader("1. Activate Service")
        container.addView(Button(this).apply {
            text = "Open Accessibility Settings (Enable Engine)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        addHeader("2. Bezel Edge Side")
        val sideSwitch = Switch(this).apply {
            text = "Buttons are on Right Edge (Off = Left Edge)"
            isChecked = prefs.getBoolean("is_right_side", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("is_right_side", isChecked).apply()
            }
        }
        container.addView(sideSwitch)

        addHeader("3. Position & Size Customization")
        addSlider("Volume Up Y-Position", "vol_up_y", 100f, screenHeight, 750f)
        addSlider("Volume Down Y-Position", "vol_down_y", 100f, screenHeight, 960f)
        addSlider("Button Length", "button_length", 60f, 450f, 170f)
        addSlider("Inward Bulge Depth", "bulge_depth", 10f, 140f, 42f)
        addSlider("Spring Speed / Stiffness", "spring_stiffness", 200f, 3000f, SpringForce.STIFFNESS_MEDIUM)

        setContentView(root)
    }
}
