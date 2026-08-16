package com.beadpixel.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.beadpixel.app.databinding.ActivitySettingsBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemUi.fitSystemBars(this, binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        rebuild()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun rebuild() {
        val root = binding.settingsContainer
        root.removeAllViews()

        addSection(root, "外观")
        addSegmented(root, "主题模式", arrayOf("跟随系统", "浅色", "深色"), SettingsRepository.themeMode(this)) { v ->
            SettingsRepository.setThemeMode(this, v)
            AppCompatDelegate.setDefaultNightMode(
                when (v) {
                    SettingsRepository.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    SettingsRepository.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
        addSegmented(root, "色号显示", arrayOf("自动", "始终显示", "关闭"), SettingsRepository.codeDisplayMode(this)) { v ->
            SettingsRepository.setCodeDisplayMode(this, v)
            rebuild()
        }
        addSegmented(root, "撤销步数上限", arrayOf("10", "30", "60", "100"), undoIndex(SettingsRepository.undoLimit(this))) { v ->
            SettingsRepository.setUndoLimit(this, intArrayOf(10, 30, 60, 100)[v])
            rebuild()
        }

        addSection(root, "编辑器")
        addSwitch(root, "绘画时自动收起面板", SettingsRepository.autoCollapseOnDraw(this)) { v ->
            SettingsRepository.setAutoCollapseOnDraw(this, v)
        }
        addSwitch(root, "透明格底纹", SettingsRepository.checkerBackground(this)) { v ->
            SettingsRepository.setCheckerBackground(this, v)
        }
        addSwitch(root, "显示缩放提示", SettingsRepository.showZoomInfo(this)) { v ->
            SettingsRepository.setShowZoomInfo(this, v)
        }

        // 高级设置（可折叠子项）
        val advHeader = LinearLayout(this)
        advHeader.orientation = LinearLayout.HORIZONTAL
        advHeader.gravity = Gravity.CENTER_VERTICAL
        advHeader.setPadding(0, dp(18), 0, dp(4))
        val advTitle = TextView(this)
        advTitle.text = "高级设置"
        advTitle.textSize = 15f
        advTitle.setTextColor(if (isNight()) Color.WHITE else Color.BLACK)
        advTitle.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val advArrow = TextView(this)
        advArrow.text = "▸"
        advArrow.textSize = 20f
        advArrow.setTextColor(if (isNight()) Color.WHITE else Color.BLACK)
        advHeader.addView(advTitle)
        advHeader.addView(advArrow)
        root.addView(advHeader, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val advContainer = LinearLayout(this)
        advContainer.orientation = LinearLayout.VERTICAL
        advContainer.visibility = View.GONE
        root.addView(advContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        advHeader.setOnClickListener {
            val vis = advContainer.visibility == View.VISIBLE
            advContainer.visibility = if (vis) View.GONE else View.VISIBLE
            advArrow.text = if (vis) "▸" else "▾"
        }
        addSwitch(advContainer, "显示网格线", SettingsRepository.showGrid(this)) { v ->
            SettingsRepository.setShowGrid(this, v)
        }
        addSegmented(advContainer, "大格线显示阈值", arrayOf("4dp", "6dp", "8dp", "10dp", "12dp"), majorIndex(SettingsRepository.majorGridThresholdDp(this))) { v ->
            SettingsRepository.setMajorGridThresholdDp(this, intArrayOf(4, 6, 8, 10, 12)[v])
            rebuild()
        }
        addSegmented(advContainer, "细网格显示阈值", arrayOf("8dp", "12dp", "16dp", "20dp", "24dp"), minorIndex(SettingsRepository.minorGridThresholdDp(this))) { v ->
            SettingsRepository.setMinorGridThresholdDp(this, intArrayOf(8, 12, 16, 20, 24)[v])
            rebuild()
        }
        addSwitch(advContainer, "双击画布适配视图", SettingsRepository.doubleTapToFit(this)) { v ->
            SettingsRepository.setDoubleTapToFit(this, v)
        }
        addSwitch(advContainer, "默认导出到相册", SettingsRepository.exportToGallery(this)) { v ->
            SettingsRepository.setExportToGallery(this, v)
        }

        addSection(root, "关于")
        val aboutText = TextView(this)
        aboutText.text = "拼豆辅助 v1.0\n\n" +
            "图标基于 Material Design Icons（Apache 2.0，© Google）\n" +
            "依赖 AndroidX / Material Components（Apache 2.0）\n" +
            "算法：CIEDE2000 色差、Floyd-Steinberg 抖动（公开算法）\n" +
            "内置色卡数据来源：HansBug/pindou-color-data（GitHub 开源项目）"
        aboutText.textSize = 12f
        aboutText.setTextColor(if (isNight()) Color.parseColor("#BDBDBD") else Color.parseColor("#616161"))
        aboutText.setLineSpacing(0f, 1.3f)
        aboutText.setPadding(0, dp(4), 0, dp(12))
        root.addView(aboutText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val resetBtn = MaterialButton(this)
        resetBtn.text = "恢复默认设置"
        resetBtn.setOnClickListener {
            SettingsRepository.reset(this)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            rebuild()
            Toast.makeText(this, "已恢复默认设置（浅色）", Toast.LENGTH_SHORT).show()
        }
        root.addView(resetBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
    }

    private fun undoIndex(limit: Int): Int = when (limit) {
        10 -> 0
        30 -> 1
        100 -> 3
        else -> 2
    }

    private fun majorIndex(v: Int): Int = when (v) {
        4 -> 0
        6 -> 1
        10 -> 3
        12 -> 4
        else -> 2
    }

    private fun minorIndex(v: Int): Int = when (v) {
        8 -> 0
        12 -> 1
        16 -> 2
        24 -> 4
        else -> 3
    }

    private fun isNight(): Boolean {
        val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun addSection(root: LinearLayout, title: String) {
        val tv = TextView(this)
        tv.text = title
        tv.textSize = 12f
        tv.setTextColor(if (isNight()) Color.parseColor("#BDBDBD") else Color.parseColor("#616161"))
        tv.setPadding(0, dp(18), 0, dp(4))
        root.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun addSegmented(root: LinearLayout, label: String, options: Array<String>, selected: Int, onSelect: (Int) -> Unit) {
        val labelTv = TextView(this)
        labelTv.text = label
        labelTv.textSize = 15f
        labelTv.setTextColor(if (isNight()) Color.WHITE else Color.BLACK)
        root.addView(labelTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val rowLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rowLp.topMargin = dp(8)
        rowLp.bottomMargin = dp(8)
        root.addView(row, rowLp)

        for ((i, opt) in options.withIndex()) {
            val btn = MaterialButton(this, null, R.attr.segmentButtonStyle)
            btn.text = opt
            btn.textSize = 12f
            btn.minHeight = 0
            btn.insetTop = 0
            btn.insetBottom = 0
            btn.setPadding(0, 0, 0, 0)
            val blp = LinearLayout.LayoutParams(0, dp(38), 1f)
            if (i > 0) blp.leftMargin = dp(4)
            btn.layoutParams = blp
            applySegmentedStyle(btn, i == selected)
            btn.setOnClickListener { onSelect(i) }
            row.addView(btn)
        }
    }

    private fun applySegmentedStyle(btn: MaterialButton, selected: Boolean) {
        val night = isNight()
        val accent = if (night) Color.WHITE else Color.BLACK
        val accentText = if (night) Color.BLACK else Color.WHITE
        val text = if (night) Color.WHITE else Color.BLACK
        val outline = if (night) Color.parseColor("#616161") else Color.parseColor("#9E9E9E")
        if (selected) {
            btn.backgroundTintList = ColorStateList.valueOf(accent)
            btn.setTextColor(accentText)
            btn.strokeWidth = 0
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            btn.setTextColor(text)
            btn.strokeColor = ColorStateList.valueOf(outline)
            btn.strokeWidth = dp(1)
        }
    }

    private fun addSwitch(root: LinearLayout, label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(6), 0, dp(6))
        root.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val tv = TextView(this)
        tv.text = label
        tv.textSize = 15f
        tv.setTextColor(if (isNight()) Color.WHITE else Color.BLACK)
        tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv)

        val sw = SwitchMaterial(this)
        sw.isChecked = value
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        row.addView(sw)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
