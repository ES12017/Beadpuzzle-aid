package com.beadpixel.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.beadpixel.app.databinding.ActivityPaletteBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PaletteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaletteBinding
    private val palettes = mutableListOf<Palette>()
    private lateinit var adapter: PaletteAdapter
    private var pendingExport: String? = null

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPalette(uri)
    }

    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && pendingExport != null) writeText(uri, pendingExport!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaletteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemUi.fitSystemBars(this, binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.btnNewPalette.setOnClickListener { showNewPaletteDialog() }
        binding.btnImport.setOnClickListener { openDoc.launch(arrayOf("*/*")) }
        adapter = PaletteAdapter(
            palettes,
            onEdit = { p ->
                startActivity(
                    Intent(this, PaletteColorsActivity::class.java)
                        .putExtra(PaletteColorsActivity.EXTRA_PALETTE_ID, p.id)
                )
            },
            onActive = { setActive(it) },
            onExport = { showExportDialog(it) },
            onRename = { showRenameDialog(it) },
            onDelete = { showDeleteDialog(it) },
            onMoveUp = { movePalette(it, -1) },
            onMoveDown = { movePalette(it, 1) }
        )
        binding.paletteList.layoutManager = LinearLayoutManager(this)
        binding.paletteList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun movePalette(p: Palette, dir: Int) {
        val idx = palettes.indexOfFirst { it.id == p.id }
        val target = idx + dir
        if (idx < 0 || target < 0 || target >= palettes.size) return
        val t = palettes[idx]
        palettes[idx] = palettes[target]
        palettes[target] = t
        PaletteStore.save(this, palettes)
        reload()
    }

    private fun reload() {
        palettes.clear()
        palettes.addAll(PaletteStore.load(this))
        adapter.notifyDataSetChanged()
    }

    private fun showNewPaletteDialog() {
        Dialogs.rename(this, "新建调色盘", "") { name ->
            val p = Palette(name = name)
            p.colors.add(PaletteColor(name = "黑色", argb = 0xFF000000.toInt()))
            palettes.add(p)
            PaletteStore.save(this, palettes)
            reload()
        }
    }

    private fun setActive(p: Palette) {
        PaletteStore.setActive(this, palettes, p.id)
        reload()
        Toast.makeText(this, "已设为当前调色盘", Toast.LENGTH_SHORT).show()
    }

    private fun showRenameDialog(p: Palette) {
        Dialogs.rename(this, "重命名调色盘", p.name) { name ->
            p.name = name
            PaletteStore.save(this, palettes)
            reload()
        }
    }

    private fun showDeleteDialog(p: Palette) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除调色盘")
            .setMessage("确定删除调色盘「" + p.name + "」？")
            .setPositiveButton("删除", null)
            .setNegativeButton("取消", null)
            .show()
            .apply {
                getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    palettes.removeAll { it.id == p.id }
                    if (palettes.isNotEmpty() && palettes.none { it.isActive }) {
                        palettes[0].isActive = true
                    }
                    PaletteStore.save(this@PaletteActivity, palettes)
                    reload()
                    dismiss()
                }
            }
    }

    private fun showExportDialog(p: Palette) {
        val formats = arrayOf("应用格式 (.json)", "通用调色盘格式 (.hxs_palette)")
        MaterialAlertDialogBuilder(this)
            .setTitle("导出调色盘")
            .setItems(formats) { _, which ->
                if (which == 0) {
                    pendingExport = PaletteStore.exportJson(p)
                    createDoc.launch(sanitize(p.name) + ".json")
                } else {
                    pendingExport = PaletteStore.exportHxs(p)
                    createDoc.launch(sanitize(p.name) + ".hxs_palette")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importPalette(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
        if (text == null || text.isBlank()) {
            Toast.makeText(this, "读取文件失败", Toast.LENGTH_SHORT).show()
            return
        }
        val p = PaletteStore.parsePaletteText(text)
        if (p == null) {
            Toast.makeText(this, "无法识别的调色盘文件", Toast.LENGTH_SHORT).show()
            return
        }
        if (p.colors.isEmpty()) {
            Toast.makeText(this, "调色盘里没有颜色", Toast.LENGTH_SHORT).show()
            return
        }
        palettes.add(p)
        PaletteStore.save(this, palettes)
        reload()
        Toast.makeText(this, "导入成功：" + p.name + "（" + p.colors.size + " 色）", Toast.LENGTH_LONG).show()
    }

    private fun writeText(uri: Uri, text: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sanitize(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|]"), "_")
}
