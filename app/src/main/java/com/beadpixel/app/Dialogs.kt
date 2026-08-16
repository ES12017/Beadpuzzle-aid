package com.beadpixel.app

import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.beadpixel.app.databinding.DialogColorEditBinding
import com.beadpixel.app.databinding.DialogExportBinding
import com.beadpixel.app.databinding.DialogImageModeBinding
import com.beadpixel.app.databinding.DialogNewProjectBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

object Dialogs {

    fun newProject(activity: AppCompatActivity, onOk: (name: String, w: Int, h: Int) -> Unit) {
        newProject(activity, 32, 32, onOk)
    }

    fun newProject(activity: AppCompatActivity, initialW: Int, initialH: Int, onOk: (name: String, w: Int, h: Int) -> Unit) {
        val binding = DialogNewProjectBinding.inflate(activity.layoutInflater)
        binding.widthInput.setText(initialW.toString())
        binding.heightInput.setText(initialH.toString())
        binding.presetGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = binding.presetGroup.findViewById<Chip>(checkedIds[0])
                val parts = chip.text.toString().split("×")
                if (parts.size == 2) {
                    binding.widthInput.setText(parts[0].trim())
                    binding.heightInput.setText(parts[1].trim())
                }
            }
        }
        val dlg = MaterialAlertDialogBuilder(activity)
            .setTitle("新建画布")
            .setView(binding.root)
            .setPositiveButton("创建", null)
            .setNegativeButton("取消", null)
            .show()
        dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = binding.projectName.text?.toString()?.trim().orEmpty().ifEmpty { "未命名" }
            val w = binding.widthInput.text?.toString()?.trim()?.toIntOrNull()
            val h = binding.heightInput.text?.toString()?.trim()?.toIntOrNull()
            if (w == null || h == null) {
                binding.errorText.text = "宽和高必须是数字"
                binding.errorText.visibility = View.VISIBLE
            } else if (w !in 1..1000 || h !in 1..1000) {
                binding.errorText.text = "尺寸范围 1 - 1000"
                binding.errorText.visibility = View.VISIBLE
            } else {
                onOk(name, w, h)
                dlg.dismiss()
            }
        }
    }

    fun colorEdit(activity: AppCompatActivity, existing: PaletteColor?, onOk: (PaletteColor) -> Unit) {
        val binding = DialogColorEditBinding.inflate(activity.layoutInflater)
        var current = existing?.argb ?: 0xFF000000.toInt()
        var syncing = false
        fun sync(c: Int) {
            if (syncing) return
            syncing = true
            current = c
            binding.colorPreview.setBackgroundColor(c)
            binding.colorHex.setText("#" + ColorUtils.hex(c))
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            binding.redBar.progress = r
            binding.greenBar.progress = g
            binding.blueBar.progress = b
            binding.redInput.setText(r.toString())
            binding.greenInput.setText(g.toString())
            binding.blueInput.setText(b.toString())
            syncing = false
        }
        existing?.let {
            binding.colorName.setText(it.name)
            binding.colorCode.setText(it.code)
        }
        sync(current)

        val sliderListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (syncing) return
                val r = binding.redBar.progress
                val g = binding.greenBar.progress
                val b = binding.blueBar.progress
                sync(0xFF000000.toInt() or (r shl 16) or (g shl 8) or b)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        binding.redBar.setOnSeekBarChangeListener(sliderListener)
        binding.greenBar.setOnSeekBarChangeListener(sliderListener)
        binding.blueBar.setOnSeekBarChangeListener(sliderListener)

        binding.redInput.addTextChangedListener { s ->
            if (syncing) return@addTextChangedListener
            val v = s?.toString()?.toIntOrNull()
            if (v != null && v in 0..255) {
                sync((current and 0xFF00FFFF.toInt()) or (v shl 16))
            }
        }
        binding.greenInput.addTextChangedListener { s ->
            if (syncing) return@addTextChangedListener
            val v = s?.toString()?.toIntOrNull()
            if (v != null && v in 0..255) {
                sync((current and 0xFFFF00FF.toInt()) or (v shl 8))
            }
        }
        binding.blueInput.addTextChangedListener { s ->
            if (syncing) return@addTextChangedListener
            val v = s?.toString()?.toIntOrNull()
            if (v != null && v in 0..255) {
                sync((current and 0xFFFFFF00.toInt()) or v)
            }
        }
        binding.colorHex.addTextChangedListener { s ->
            if (syncing) return@addTextChangedListener
            val txt = s?.toString().orEmpty()
            val c = ColorUtils.parseHex(txt)
            if (c != null) sync(c)
        }

        val dlg = MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing == null) "添加颜色" else "编辑颜色")
            .setView(binding.root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .show()
        dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val hexTxt = binding.colorHex.text?.toString()?.trim().orEmpty()
            val parsed = ColorUtils.parseHex(hexTxt) ?: current
            onOk(
                PaletteColor(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    name = binding.colorName.text?.toString()?.trim().orEmpty(),
                    code = binding.colorCode.text?.toString()?.trim().orEmpty(),
                    argb = parsed
                )
            )
            dlg.dismiss()
        }
    }

    fun imageMode(activity: AppCompatActivity, onOk: (mode: Int) -> Unit) {
        val binding = DialogImageModeBinding.inflate(activity.layoutInflater)
        MaterialAlertDialogBuilder(activity)
            .setTitle("图片转像素")
            .setView(binding.root)
            .setPositiveButton("转换", null)
            .setNegativeButton("取消", null)
            .show()
            .apply {
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val mode = if (binding.modeFit.isChecked) 1 else 0
                    onOk(mode)
                    dismiss()
                }
            }
    }


    fun exportPng(activity: AppCompatActivity, onOk: (scale: Int, grid: Boolean, showCode: Boolean) -> Unit) {
        val binding = DialogExportBinding.inflate(activity.layoutInflater)
        binding.scaleInput.setText("20")
        MaterialAlertDialogBuilder(activity)
            .setTitle("导出图片")
            .setView(binding.root)
            .setPositiveButton("导出", null)
            .setNegativeButton("取消", null)
            .show()
            .apply {
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val s = binding.scaleInput.text?.toString()?.trim()?.toIntOrNull() ?: 20
                    onOk(s.coerceIn(1, 100), binding.gridCheck.isChecked, binding.codeCheck.isChecked)
                    dismiss()
                }
            }
    }

    fun rename(activity: AppCompatActivity, title: String, initial: String, onOk: (String) -> Unit) {
        val et = TextInputEditText(activity)
        et.setText(initial)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(activity, 24), dp(activity, 8), dp(activity, 24), 0)
        val wrap = FrameLayout(activity)
        wrap.addView(et, lp)
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .show()
            .apply {
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val v = et.text?.toString()?.trim()
                    if (!v.isNullOrEmpty()) {
                        onOk(v)
                        dismiss()
                    }
                }
            }
    }

    private fun dp(activity: AppCompatActivity, v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()
}
