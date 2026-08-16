package com.beadpixel.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beadpixel.app.databinding.ItemColorBarBinding

class ColorBarAdapter(
    private val items: List<PaletteColor>,
    var selectedIndex: Int = 0,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ColorBarAdapter.VH>() {

    class VH(val binding: ItemColorBarBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemColorBarBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        val b = holder.binding
        b.swatch.setBackgroundColor(c.argb)
        val code = if (c.code.isNotBlank()) c.code else ""
        b.codeText.text = code
        b.codeText.visibility = if (code.isEmpty()) android.view.View.INVISIBLE else android.view.View.VISIBLE
        b.codeText.setTextColor(ColorUtils.contrastText(c.argb))
        b.root.isSelected = position == selectedIndex
        b.root.setOnClickListener { onClick(position) }
    }
}
