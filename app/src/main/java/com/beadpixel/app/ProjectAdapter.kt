package com.beadpixel.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.beadpixel.app.databinding.ItemProjectBinding

class ProjectAdapter(
    private val items: List<PixelProject>,
    private val onClick: (PixelProject) -> Unit,
    private val onLongClick: (PixelProject) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.VH>() {

    class VH(val binding: ItemProjectBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemProjectBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val b = holder.binding
        b.projectName.text = (if (p.pinned) "📌 " else "") + p.name
        b.projectMeta.text = p.width.toString() + " × " + p.height + "    创建于 " + formatTime(p.createdAt)
        b.thumb.setImageBitmap(makeThumb(p))
        b.root.setOnClickListener { onClick(p) }
        b.root.setOnLongClickListener { onLongClick(p); true }
    }

    private fun formatTime(t: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return fmt.format(java.util.Date(t))
    }

    /**
     * 封面缩略图：按目标尺寸逐像素采样源画布（最近邻），
     * 无论画布多大都只需 O(64*64) 次运算，大画布也不会空白或卡顿。
     */
    private fun makeThumb(p: PixelProject): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val paint = Paint()
        val scale = size.toFloat() / maxOf(p.width, p.height)
        val drawW = maxOf(1, (p.width * scale).toInt())
        val drawH = maxOf(1, (p.height * scale).toInt())
        val offX = (size - drawW) / 2
        val offY = (size - drawH) / 2
        val w = p.width
        for (ty in 0 until drawH) {
            val sy = (ty * p.height) / drawH
            val rowBase = sy * w
            for (tx in 0 until drawW) {
                val sx = (tx * w) / drawW
                val c = p.pixels[rowBase + sx]
                if (c != 0) {
                    paint.color = c
                    cv.drawRect(
                        (offX + tx).toFloat(),
                        (offY + ty).toFloat(),
                        (offX + tx + 1).toFloat(),
                        (offY + ty + 1).toFloat(),
                        paint
                    )
                }
            }
        }
        return bmp
    }
}
