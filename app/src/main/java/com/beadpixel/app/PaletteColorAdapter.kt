package com.beadpixel.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beadpixel.app.databinding.ItemPaletteColorBinding

class PaletteColorAdapter(
    private val items: List<PaletteColor>,
    private val onClick: (PaletteColor) -> Unit,
    private val onLongClick: (PaletteColor) -> Unit
) : RecyclerView.Adapter<PaletteColorAdapter.VH>() {

    class VH(val binding: ItemPaletteColorBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPaletteColorBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        val b = holder.binding
        val gd = android.graphics.drawable.GradientDrawable()
        gd.cornerRadius = 6f * b.colorSwatch.resources.displayMetrics.density
        gd.setColor(c.argb)
        b.colorSwatch.background = gd
        b.colorLabel.text = c.label()
        b.colorHex.text = "#" + ColorUtils.hex(c.argb)
        b.root.setOnClickListener { onClick(c) }
        b.root.setOnLongClickListener { onLongClick(c); true }
    }
}
