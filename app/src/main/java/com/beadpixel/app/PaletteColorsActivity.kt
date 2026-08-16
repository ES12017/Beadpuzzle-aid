package com.beadpixel.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.beadpixel.app.databinding.ActivityPaletteColorsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PaletteColorsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PALETTE_ID = "palette_id"
    }

    private lateinit var binding: ActivityPaletteColorsBinding
    private var palette: Palette? = null
    private var paletteId: String? = null
    private val colors = mutableListOf<PaletteColor>()
    private lateinit var adapter: PaletteColorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaletteColorsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemUi.fitSystemBars(this, binding.root)
        paletteId = intent.getStringExtra(EXTRA_PALETTE_ID)
        reloadPalette()
        if (palette == null) {
            Toast.makeText(this, "调色盘不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.title = palette!!.name
        binding.btnAdd.setOnClickListener { showAddColor() }
        binding.btnActive.setOnClickListener { setActive() }
        adapter = PaletteColorAdapter(
            colors,
            onClick = { showEditColor(it) },
            onLongClick = { showDeleteColor(it) }
        )
        binding.colorGrid.layoutManager = GridLayoutManager(
            this,
            resources.getInteger(R.integer.palette_grid_span)
        )
        binding.colorGrid.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        reloadPalette()
        if (::adapter.isInitialized) adapter.notifyDataSetChanged()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun reloadPalette() {
        val id = paletteId ?: return
        val list = PaletteStore.load(this)
        palette = list.firstOrNull { it.id == id }
        colors.clear()
        palette?.let { colors.addAll(it.colors) }
    }

    private fun save() {
        val p = palette ?: return
        p.colors.clear()
        p.colors.addAll(colors)
        val list = PaletteStore.load(this)
        val idx = list.indexOfFirst { it.id == p.id }
        if (idx >= 0) list[idx] = p else list.add(p)
        PaletteStore.save(this, list)
    }

    private fun showAddColor() {
        Dialogs.colorEdit(this, null) { c ->
            colors.add(c)
            save()
            adapter.notifyDataSetChanged()
        }
    }

    private fun showEditColor(c: PaletteColor) {
        Dialogs.colorEdit(this, c) { updated ->
            val idx = colors.indexOfFirst { it.id == c.id }
            if (idx >= 0) colors[idx] = updated
            save()
            adapter.notifyDataSetChanged()
        }
    }

    private fun showDeleteColor(c: PaletteColor) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除颜色")
            .setMessage("删除颜色 " + c.label() + "？")
            .setPositiveButton("删除", null)
            .setNegativeButton("取消", null)
            .show()
            .apply {
                getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    colors.removeAll { it.id == c.id }
                    save()
                    adapter.notifyDataSetChanged()
                    dismiss()
                }
            }
    }

    private fun setActive() {
        val p = palette ?: return
        val list = PaletteStore.load(this)
        PaletteStore.setActive(this, list, p.id)
        Toast.makeText(this, "已设为当前调色盘", Toast.LENGTH_SHORT).show()
    }
}
