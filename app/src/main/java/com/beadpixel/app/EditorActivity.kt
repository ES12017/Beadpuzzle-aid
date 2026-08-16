package com.beadpixel.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.beadpixel.app.databinding.ActivityEditorBinding
import com.beadpixel.app.databinding.ItemHudSwatchBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.DialogInterface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class EditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
    }

    private lateinit var binding: ActivityEditorBinding
    private var project: PixelProject? = null
    private var palette: Palette? = null
    private var currentColor = 0xFF000000.toInt()
    private val toolButtons = mutableListOf<ImageButton>()
    private var selectedHudIndex = 0
    private var panelExpanded = true
    private var pendingPng: Bitmap? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    private val createPng = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) writePng(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemUi.fitSystemBars(this, binding.root)

        val id = intent.getStringExtra(EXTRA_PROJECT_ID)
        val p = id?.let { ProjectStore.load(this, it) }
        if (p == null) {
            Toast.makeText(this, "画布不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        project = p

        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = p.name
        binding.toolbar.setNavigationIcon(R.drawable.ic_back)
        binding.toolbar.setNavigationOnClickListener { finish() }

        applySettingsToCanvas()
        binding.canvas.setCanvas(p.width, p.height, p.pixels, emptyList())
        binding.canvas.onStrokeStart = {
            if (SettingsRepository.autoCollapseOnDraw(this) && panelExpanded) {
                setPanelExpanded(false)
            }
        }
        binding.canvas.onStrokeEnd = { saveProject() }
        binding.canvas.onPickedColor = { c ->
            setCurrentColor(c)
            setTool(CanvasView.Tool.BRUSH)
        }
        binding.canvas.onUndoStateChanged = { u, r ->
            binding.btnUndo.isEnabled = u
            binding.btnRedo.isEnabled = r
            binding.btnUndo.alpha = if (u) 1f else 0.35f
            binding.btnRedo.alpha = if (r) 1f else 0.35f
        }
        binding.canvas.onTransformChanged = {
            val cell = binding.canvas.cellSizeDp()
            val txt = String.format("%.0f dp/格", cell)
            if (binding.zoomInfo.text != txt) binding.zoomInfo.text = txt
        }
        binding.canvas.fitToView()

        toolButtons.add(binding.btnBrush)
        toolButtons.add(binding.btnEraser)
        toolButtons.add(binding.btnFill)
        toolButtons.add(binding.btnPick)
        toolButtons.add(binding.btnPan)
        binding.btnBrush.setOnClickListener { setTool(CanvasView.Tool.BRUSH) }
        binding.btnEraser.setOnClickListener { setTool(CanvasView.Tool.ERASER) }
        binding.btnFill.setOnClickListener { setTool(CanvasView.Tool.FILL) }
        binding.btnPick.setOnClickListener { setTool(CanvasView.Tool.EYEDROPPER) }
        binding.btnPan.setOnClickListener { setTool(CanvasView.Tool.PAN) }

        binding.btnUndo.setOnClickListener {
            binding.canvas.undo()
            saveProject()
        }
        binding.btnRedo.setOnClickListener {
            binding.canvas.redo()
            saveProject()
        }
        binding.btnPreview.setOnClickListener { openPreview() }
        binding.btnImage.setOnClickListener { pickImage.launch("image/*") }
        binding.btnPalette.setOnClickListener { showPalettePickDialog() }
        binding.btnExport.setOnClickListener { showExportDialog() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnStats.setOnClickListener { showStatsDialog() }
        binding.btnTogglePanel.setOnClickListener { setPanelExpanded(!panelExpanded) }

        applyPalette(PaletteStore.activePalette(this))
        setTool(CanvasView.Tool.BRUSH)
        setPanelExpanded(true)
    }

    override fun onResume() {
        super.onResume()
        applySettingsToCanvas()
    }

    override fun onPause() {
        super.onPause()
        saveProject(false)
    }

    private fun applySettingsToCanvas() {
        binding.canvas.showGrid = SettingsRepository.showGrid(this)
        binding.canvas.checkerBackground = SettingsRepository.checkerBackground(this)
        binding.canvas.codeDisplayMode = SettingsRepository.codeDisplayMode(this)
        binding.canvas.undoLimit = effectiveUndoLimit()
        binding.canvas.majorGridThreshold = SettingsRepository.majorGridThresholdDp(this).toFloat()
        binding.canvas.minorGridThreshold = SettingsRepository.minorGridThresholdDp(this).toFloat()
        binding.canvas.doubleTapToFit = SettingsRepository.doubleTapToFit(this)
        binding.zoomInfo.visibility = if (SettingsRepository.showZoomInfo(this)) View.VISIBLE else View.GONE
    }

    private fun showStatsDialog() {
        val p = project ?: return
        val pal = palette ?: return
        val counts = HashMap<Int, Int>()
        for (i in p.pixels.indices) {
            val c = p.pixels[i]
            if (c != 0) counts[c] = (counts[c] ?: 0) + 1
        }
        if (counts.isEmpty()) {
            Toast.makeText(this, "画布还是空的，先画一些吧", Toast.LENGTH_SHORT).show()
            return
        }
        val byArgb = HashMap<Int, PaletteColor>()
        for (pc in pal.colors) byArgb[pc.argb] = pc
        val rows = counts.entries.map { (argb, cnt) ->
            Triple(byArgb[argb] ?: PaletteColor(argb = argb), argb, cnt)
        }.sortedByDescending { it.third }
        val total = p.width * p.height
        val usedTotal = counts.values.sum()

        val scroll = ScrollView(this)
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(dpInt(20), dpInt(8), dpInt(20), dpInt(8))
        val header = TextView(this)
        header.text = "画布 " + p.width + "×" + p.height + "　已用豆 " + usedTotal + " / " + total + " 格"
        header.textSize = 13f
        header.setPadding(0, 0, 0, dpInt(8))
        container.addView(header)
        for ((pc, argb, cnt) in rows) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dpInt(4), 0, dpInt(4))
            val sw = View(this)
            val gd = GradientDrawable()
            gd.cornerRadius = dp(5f)
            gd.setColor(argb)
            sw.background = gd
            row.addView(sw, LinearLayout.LayoutParams(dpInt(28), dpInt(28)))
            val label = TextView(this)
            val pct = (100.0 * cnt / usedTotal).toInt()
            label.text = pc.label() + "　×" + cnt + "　(" + pct + "%)"
            label.textSize = 14f
            label.setPadding(dpInt(10), 0, 0, 0)
            label.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(label)
            container.addView(row)
        }
        scroll.addView(container)
        MaterialAlertDialogBuilder(this)
            .setTitle("豆量统计")
            .setView(scroll)
            .setPositiveButton("好的", null)
            .show()
    }

    private fun dpInt(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun setPanelExpanded(expanded: Boolean) {
        panelExpanded = expanded
        binding.toolsPanel.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.btnTogglePanel.setImageResource(if (expanded) R.drawable.ic_collapse else R.drawable.ic_expand)
        binding.btnTogglePanel.contentDescription = if (expanded) "收起面板" else "展开面板"
    }

    private fun applyPalette(p: Palette?) {
        val pal = p ?: Palette(name = "空")
        palette = pal
        binding.canvas.palette = pal.colors
        binding.toolbar.subtitle = pal.name
        selectedHudIndex = 0
        if (pal.colors.isNotEmpty()) {
            setCurrentColor(pal.colors[0].argb)
        } else {
            setCurrentColor(0xFF000000.toInt())
        }
        buildColorHud()
        setTool(CanvasView.Tool.BRUSH)
    }

    private fun buildColorHud() {
        val pal = palette ?: return
        binding.hudSwatches.removeAllViews()
        for ((i, c) in pal.colors.withIndex()) {
            val item = ItemHudSwatchBinding.inflate(LayoutInflater.from(this), binding.hudSwatches, false)
            val gd = android.graphics.drawable.GradientDrawable()
            gd.cornerRadius = dp(5f)
            gd.setColor(c.argb)
            item.swatch.background = gd
            val label = if (c.code.isNotBlank()) c.code else if (c.name.isNotBlank()) c.name else ""
            item.codeText.text = label
            item.codeText.visibility = if (label.isEmpty()) View.INVISIBLE else View.VISIBLE
            item.codeText.setTextColor(hudTextColor())
            item.swatchWrap.isSelected = (i == selectedHudIndex)
            item.root.setOnClickListener { selectHudColor(i) }
            binding.hudSwatches.addView(item.root)
        }
        updateHudIndicator()
    }

    private fun selectHudColor(i: Int) {
        val pal = palette ?: return
        val c = pal.colors.getOrNull(i) ?: return
        selectedHudIndex = i
        setCurrentColor(c.argb)
        for (j in 0 until binding.hudSwatches.childCount) {
            val child = binding.hudSwatches.getChildAt(j)
            if (child is android.view.ViewGroup) {
                child.findViewById<View>(R.id.swatchWrap)?.isSelected = (j == i)
            }
        }
        setTool(CanvasView.Tool.BRUSH)
    }

    private fun updateHudIndicator() {
        val gd = android.graphics.drawable.GradientDrawable()
        gd.cornerRadius = dp(5f)
        gd.setColor(currentColor)
        binding.hudCurrent.background = gd
        binding.hudHex.text = "#" + ColorUtils.hex(currentColor)
        binding.hudHex.setTextColor(hudTextColor())
    }

    private fun isNight(): Boolean {
        val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun hudTextColor(): Int = if (isNight()) Color.WHITE else Color.BLACK

    private fun setCurrentColor(c: Int) {
        currentColor = c
        binding.canvas.currentColor = c
        updateHudIndicator()
    }

    private fun setTool(t: CanvasView.Tool) {
        binding.canvas.tool = t
        for (b in toolButtons) b.isSelected = false
        val map = mapOf(
            CanvasView.Tool.BRUSH to binding.btnBrush,
            CanvasView.Tool.ERASER to binding.btnEraser,
            CanvasView.Tool.FILL to binding.btnFill,
            CanvasView.Tool.EYEDROPPER to binding.btnPick,
            CanvasView.Tool.PAN to binding.btnPan
        )
        map[t]?.let { it.isSelected = true }
    }

    private fun openPreview() {
        val p = project ?: return
        startActivity(
            Intent(this, PreviewActivity::class.java)
                .putExtra(PreviewActivity.EXTRA_PROJECT_ID, p.id)
        )
    }

    private fun saveProject(async: Boolean = true) {
        val p = project ?: return
        p.pixels = binding.canvas.pixels
        p.updatedAt = System.currentTimeMillis()
        if (async) ProjectStore.saveAsync(this, p) else ProjectStore.saveSync(this, p)
    }

    /** 大画布撤销快照占用内存大，按画布尺寸动态压缩撤销步数，防止 OOM。 */
    private fun effectiveUndoLimit(): Int {
        val p = project ?: return SettingsRepository.undoLimit(this)
        val config = SettingsRepository.undoLimit(this)
        val perStep = p.width.toLong() * p.height.toLong() * 4L
        val budget = 96L * 1024 * 1024
        if (perStep <= 0) return config
        val dyn = (budget / perStep).toInt().coerceAtLeast(1)
        return minOf(config, dyn)
    }

    private fun showPalettePickDialog() {
        val list = PaletteStore.load(this)
        if (list.isEmpty()) {
            Toast.makeText(this, "没有调色盘，请先创建", Toast.LENGTH_SHORT).show()
            return
        }
        val names = list.map { it.name + if (it.isActive) "（当前）" else "" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("选择调色盘")
            .setItems(names) { _, which -> setActive(list[which].id) }
            .setNeutralButton("管理调色盘") { _, _ ->
                startActivity(Intent(this, PaletteActivity::class.java))
            }
            .show()
    }

    private fun setActive(id: String) {
        val list = PaletteStore.load(this)
        PaletteStore.setActive(this, list, id)
        applyPalette(list.firstOrNull { it.id == id })
    }

    private fun onImagePicked(uri: Uri) {
        val p = project ?: return
        val pal = palette ?: return
        if (pal.colors.isEmpty()) {
            Toast.makeText(this, "当前调色盘没有颜色，请先添加颜色", Toast.LENGTH_LONG).show()
            return
        }
        val bmp = ImageConverter.loadBitmap(this, uri)
        if (bmp == null) {
            Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show()
            return
        }
        Dialogs.imageMode(this) { mode ->
            val result = ImageConverter.convert(bmp, p.width, p.height, pal.colors, mode)
            binding.canvas.pixels = result
            binding.canvas.fitToView()
            saveProject()
            Toast.makeText(this, "转换完成", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExportDialog() {
        val p = project ?: return
        Dialogs.exportPng(this) { scale, grid, showCode ->
            val maxDim = 4096
            val maxSide = maxOf(p.width, p.height)
            val effScale = if (maxSide * scale > maxDim) maxOf(1, maxDim / maxSide) else scale
            if (effScale != scale) {
                Toast.makeText(this, "画布较大，实际导出为 ${p.width * effScale}×${p.height * effScale}px（放大 ${effScale} 倍）", Toast.LENGTH_LONG).show()
            }
            val bmp = buildExportBitmap(p, effScale, grid, showCode)
            showExportResult(bmp, p)
        }
    }

    private fun showExportResult(bmp: Bitmap, p: PixelProject) {
        val name = p.name + "_" + p.width + "x" + p.height + ".png"
        MaterialAlertDialogBuilder(this)
            .setTitle("导出完成")
            .setMessage("已生成 ${bmp.width}×${bmp.height} 图纸，可保存到相册或分享给他人对照使用。")
            .setPositiveButton("保存", null)
            .setNeutralButton("分享", null)
            .setNegativeButton("关闭", null)
            .show()
            .apply {
                getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    if (SettingsRepository.exportToGallery(this@EditorActivity) && Build.VERSION.SDK_INT >= 29) {
                        saveToGallery(bmp, name)
                    } else {
                        pendingPng = bmp
                        createPng.launch(name)
                    }
                    dismiss()
                }
                getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                    shareBitmap(bmp, name)
                }
            }
    }

    private fun shareBitmap(bmp: Bitmap, name: String) {
        try {
            val dir = File(cacheDir, "shared").apply { mkdirs() }
            val f = File(dir, name)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "com.beadpixel.app.fileprovider", f)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享图纸"))
        } catch (e: Exception) {
            Toast.makeText(this, "分享失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }


    private fun saveToGallery(bmp: Bitmap, name: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/拼豆像素画")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                Toast.makeText(this, "保存到相册失败", Toast.LENGTH_SHORT).show()
                return
            }
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            Toast.makeText(this, "已保存到相册（Pictures/拼豆像素画）", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "保存到相册失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildExportBitmap(p: PixelProject, scalePx: Int, grid: Boolean, showCode: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(p.width * scalePx, p.height * scalePx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val paint = Paint()
        val codeMap = if (showCode) buildCodeMap() else null
        for (y in 0 until p.height) {
            for (x in 0 until p.width) {
                val c = p.pixels[y * p.width + x]
                if (c != 0) {
                    paint.color = c
                    cv.drawRect(
                        (x * scalePx).toFloat(),
                        (y * scalePx).toFloat(),
                        ((x + 1) * scalePx).toFloat(),
                        ((y + 1) * scalePx).toFloat(),
                        paint
                    )
                    if (codeMap != null) {
                        val t = codeMap[c] ?: ("#" + ColorUtils.hex(c))
                        drawExportCellText(cv, t, (x * scalePx).toFloat(), (y * scalePx).toFloat(), scalePx.toFloat(), c)
                    }
                }
            }
        }
        if (grid) {
            val gp = Paint()
            gp.style = Paint.Style.STROKE
            gp.strokeWidth = 1f
            gp.color = 0x55000000.toInt()
            for (x in 0..p.width) {
                cv.drawLine(
                    (x * scalePx).toFloat(),
                    0f,
                    (x * scalePx).toFloat(),
                    (p.height * scalePx).toFloat(),
                    gp
                )
            }
            for (y in 0..p.height) {
                cv.drawLine(
                    0f,
                    (y * scalePx).toFloat(),
                    (p.width * scalePx).toFloat(),
                    (y * scalePx).toFloat(),
                    gp
                )
            }
        }
        return bmp
    }

    private fun buildCodeMap(): HashMap<Int, String> {
        val m = HashMap<Int, String>()
        for (c in (palette?.colors ?: emptyList())) {
            if (!m.containsKey(c.argb)) m[c.argb] = c.codeText()
        }
        return m
    }

    private fun drawExportCellText(cv: Canvas, text: String, left: Float, top: Float, cell: Float, color: Int): Boolean {
        if (text.isEmpty()) return false
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ColorUtils.contrastText(color)
        var size = cell * 0.42f
        paint.textSize = size
        val maxW = cell * 0.92f
        if (text.length * size * 0.62f > maxW) {
            var guard = 0
            while (guard < 8 && size > 3f && paint.measureText(text) > maxW) {
                size *= 0.82f
                paint.textSize = size
                guard++
            }
        }
        val x = left + cell / 2f
        val y = top + cell / 2f - (paint.ascent() + paint.descent()) / 2f
        cv.drawText(text, x, y, paint)
        return true
    }


    private fun writePng(uri: Uri) {
        val bmp = pendingPng ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
