package com.beadpixel.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.beadpixel.app.databinding.ItemPaletteBinding

class PaletteAdapter(
    private val items: List<Palette>,
    private val onEdit: (Palette) -> Unit,
    private val onActive: (Palette) -> Unit,
    private val onExport: (Palette) -> Unit,
    private val onRename: (Palette) -> Unit,
    private val onDelete: (Palette) -> Unit,
    private val onMoveUp: (Palette) -> Unit,
    private val onMoveDown: (Palette) -> Unit
) : RecyclerView.Adapter<PaletteAdapter.VH>() {

    class VH(val binding: ItemPaletteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPaletteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val b = holder.binding
        b.paletteName.text = p.name
        b.colorCount.text = p.colors.size.toString() + " 色"
        b.activeBadge.visibility = if (p.isActive) View.VISIBLE else View.GONE
        b.swatchStrip.removeAllViews()
        val ctx = b.swatchStrip.context
        val sw = dp(ctx, 26)
        for ((i, c) in p.colors.withIndex()) {
            if (i >= 40) break
            val v = View(ctx)
            val lp = LinearLayout.LayoutParams(sw, sw)
            lp.setMargins(dp(ctx, 2), 0, dp(ctx, 2), 0)
            v.layoutParams = lp
            v.setBackgroundColor(c.argb)
            b.swatchStrip.addView(v)
        }
        b.btnUp.setOnClickListener { onMoveUp(p) }
        b.btnDown.setOnClickListener { onMoveDown(p) }
        b.btnEdit.setOnClickListener { onEdit(p) }
        b.btnActive.setOnClickListener { onActive(p) }
        b.btnExport.setOnClickListener { onExport(p) }
        b.btnRename.setOnClickListener { onRename(p) }
        b.btnDelete.setOnClickListener { onDelete(p) }
    }

    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
}
